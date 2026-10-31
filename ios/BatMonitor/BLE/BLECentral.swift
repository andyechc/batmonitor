import CoreBluetooth
import Foundation

struct ScannedDevice: Identifiable, Equatable {
    let id: UUID
    let name: String
    let rssi: Int
}

struct BatteryReading: Equatable {
    let basic: BMSBasicInfo
    let cells: [Double]
    let receivedAt: Date
}

enum BLEError: LocalizedError {
    case bluetoothOff
    case unauthorized
    case noConnect
    case noResponse
    case notConnected
    case writeFailed
    case invalidData

    var errorDescription: String? {
        switch self {
        case .bluetoothOff: String(localized: "ble.bluetoothOff")
        case .unauthorized: String(localized: "ble.unauthorized")
        case .noConnect: String(localized: "ble.noConnect")
        case .noResponse: String(localized: "ble.noResponse")
        case .notConnected: String(localized: "ble.notConnected")
        case .writeFailed: String(localized: "ble.writeFailed")
        case .invalidData: String(localized: "ble.invalidData")
        }
    }
}

/// Owns the CoreBluetooth central. All callbacks run on the main queue.
final class BLECentral: NSObject {
    static let shared = BLECentral()

    static let serviceUUID = CBUUID(string: "FF00")
    static let notifyUUID = CBUUID(string: "FF01")
    static let writeUUID = CBUUID(string: "FF02")

    private static let connectTimeout: TimeInterval = 20
    private static let settleDelay: UInt64 = 1_000_000_000

    private struct Waiter {
        let continuation: CheckedContinuation<BMSFrame, Error>
        let timeout: DispatchWorkItem
    }

    private final class Link {
        let peripheral: CBPeripheral
        var writeCharacteristic: CBCharacteristic?
        var notifyCharacteristic: CBCharacteristic?
        var connectContinuation: CheckedContinuation<Void, Error>?
        var connectTimeout: DispatchWorkItem?
        var waiters: [UInt8: Waiter] = [:]
        let parser = BMSFrameParser()

        init(peripheral: CBPeripheral) {
            self.peripheral = peripheral
        }
    }

    private lazy var manager = CBCentralManager(delegate: self, queue: .main)
    private var known: [UUID: CBPeripheral] = [:]
    private var links: [UUID: Link] = [:]
    private var scanHandler: ((ScannedDevice) -> Void)?

    private override init() {
        super.init()
    }

    /// Waits briefly while the state is still unknown, as the manager starts up.
    func waitUntilSettled() async throws {
        var attempts = 0
        while (manager.state == .unknown || manager.state == .resetting) && attempts < 25 {
            try await Task.sleep(nanoseconds: 200_000_000)
            attempts += 1
        }
        switch manager.state {
        case .poweredOn: return
        case .unauthorized: throw BLEError.unauthorized
        default: throw BLEError.bluetoothOff
        }
    }

    func startScan(onFound: @escaping (ScannedDevice) -> Void) async throws {
        try await waitUntilSettled()
        scanHandler = onFound
        manager.scanForPeripherals(
            withServices: [Self.serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
    }

    func stopScan() {
        scanHandler = nil
        manager.stopScan()
    }

    func connect(id: UUID) async throws {
        try await waitUntilSettled()
        disconnect(id)
        let peripheral = try await resolvePeripheral(id: id)
        let link = Link(peripheral: peripheral)
        links[id] = link
        peripheral.delegate = self

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            link.connectContinuation = continuation
            let timeout = DispatchWorkItem { [weak self] in
                self?.expireConnect(id: id)
            }
            link.connectTimeout = timeout
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.connectTimeout, execute: timeout)
            manager.connect(peripheral, options: nil)
        }
        try await Task.sleep(nanoseconds: Self.settleDelay)
    }

    func disconnect(_ id: UUID) {
        guard let link = links.removeValue(forKey: id) else { return }
        teardown(link, error: BLEError.notConnected)
        manager.cancelPeripheralConnection(link.peripheral)
    }

    func readBattery(id: UUID) async throws -> BatteryReading {
        let basic = try await command(
            id: id,
            payload: BMSCodec.readCommand(BMSCommand.basicInfo),
            cmd: BMSCommand.basicInfo
        )
        let cells = try await command(
            id: id,
            payload: BMSCodec.readCommand(BMSCommand.cellVoltages),
            cmd: BMSCommand.cellVoltages
        )
        guard let info = BMSCodec.parseBasicInfo(basic.data) else { throw BLEError.invalidData }
        return BatteryReading(
            basic: info,
            cells: BMSCodec.parseCellVoltages(cells.data),
            receivedAt: Date()
        )
    }

    /// Sends a frame and resolves with the response that carries the same command byte.
    func command(id: UUID, payload: [UInt8], cmd: UInt8, timeout: TimeInterval = 3) async throws -> BMSFrame {
        guard let link = links[id], let characteristic = link.writeCharacteristic else {
            throw BLEError.notConnected
        }
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<BMSFrame, Error>) in
            if let previous = link.waiters.removeValue(forKey: cmd) {
                previous.timeout.cancel()
                previous.continuation.resume(throwing: BLEError.noResponse)
            }
            let timeoutItem = DispatchWorkItem { [weak link] in
                guard let link, let waiter = link.waiters.removeValue(forKey: cmd) else { return }
                waiter.continuation.resume(throwing: BLEError.noResponse)
            }
            link.waiters[cmd] = Waiter(continuation: continuation, timeout: timeoutItem)
            DispatchQueue.main.asyncAfter(deadline: .now() + timeout, execute: timeoutItem)

            let type: CBCharacteristicWriteType = characteristic.properties.contains(.writeWithoutResponse)
                ? .withoutResponse
                : .withResponse
            link.peripheral.writeValue(Data(payload), for: characteristic, type: type)
        }
    }

    private func resolvePeripheral(id: UUID) async throws -> CBPeripheral {
        if let peripheral = known[id] ?? manager.retrievePeripherals(withIdentifiers: [id]).first {
            known[id] = peripheral
            return peripheral
        }
        let scanning = scanHandler == nil
        if scanning {
            manager.scanForPeripherals(withServices: [Self.serviceUUID], options: nil)
        }
        defer {
            if scanning { manager.stopScan() }
        }
        for _ in 0..<40 {
            if let peripheral = known[id] { return peripheral }
            try await Task.sleep(nanoseconds: 200_000_000)
        }
        throw BLEError.noConnect
    }

    private func expireConnect(id: UUID) {
        guard let link = links.removeValue(forKey: id) else { return }
        teardown(link, error: BLEError.noConnect)
        manager.cancelPeripheralConnection(link.peripheral)
    }

    private func finishConnect(_ link: Link, result: Result<Void, Error>) {
        guard let continuation = link.connectContinuation else { return }
        link.connectContinuation = nil
        link.connectTimeout?.cancel()
        continuation.resume(with: result)
    }

    private func failConnect(peripheral: CBPeripheral, error: Error) {
        guard let link = links.removeValue(forKey: peripheral.identifier) else { return }
        teardown(link, error: error)
        manager.cancelPeripheralConnection(peripheral)
    }

    private func teardown(_ link: Link, error: Error) {
        link.connectTimeout?.cancel()
        if let continuation = link.connectContinuation {
            link.connectContinuation = nil
            continuation.resume(throwing: error)
        }
        for cmd in Array(link.waiters.keys) {
            resolve(link, cmd: cmd, with: .failure(error))
        }
    }

    private func resolve(_ link: Link, cmd: UInt8, with result: Result<BMSFrame, Error>) {
        guard let waiter = link.waiters.removeValue(forKey: cmd) else { return }
        waiter.timeout.cancel()
        waiter.continuation.resume(with: result)
    }
}

extension BLECentral: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {}

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        known[peripheral.identifier] = peripheral
        guard let handler = scanHandler else { return }
        let advertised = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let name = advertised ?? peripheral.name ?? String(localized: "ble.unnamed")
        handler(ScannedDevice(id: peripheral.identifier, name: name, rssi: RSSI.intValue))
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([Self.serviceUUID])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        failConnect(peripheral: peripheral, error: BLEError.noConnect)
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        guard let link = links.removeValue(forKey: peripheral.identifier) else { return }
        teardown(link, error: BLEError.notConnected)
    }
}

extension BLECentral: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil,
              let service = peripheral.services?.first(where: { $0.uuid == Self.serviceUUID }) else {
            failConnect(peripheral: peripheral, error: BLEError.noConnect)
            return
        }
        peripheral.discoverCharacteristics([Self.notifyUUID, Self.writeUUID], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let link = links[peripheral.identifier] else { return }
        let characteristics = service.characteristics ?? []
        link.notifyCharacteristic = characteristics.first { $0.uuid == Self.notifyUUID }
        link.writeCharacteristic = characteristics.first { $0.uuid == Self.writeUUID }
        guard error == nil, let notify = link.notifyCharacteristic, link.writeCharacteristic != nil else {
            failConnect(peripheral: peripheral, error: BLEError.noConnect)
            return
        }
        peripheral.setNotifyValue(true, for: notify)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == Self.notifyUUID, let link = links[peripheral.identifier] else { return }
        guard error == nil, characteristic.isNotifying else {
            failConnect(peripheral: peripheral, error: BLEError.noConnect)
            return
        }
        finishConnect(link, result: .success(()))
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == Self.notifyUUID, error == nil,
              let value = characteristic.value,
              let link = links[peripheral.identifier] else { return }
        let bytes = [UInt8](value)
        if let ack = BMSCodec.parseNameResponse(bytes) {
            resolve(link, cmd: BMSCommand.bleName, with: .success(ack))
            return
        }
        for frame in link.parser.push(bytes) {
            resolve(link, cmd: frame.cmd, with: .success(frame))
        }
    }
}
