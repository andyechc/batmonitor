import SwiftUI

struct RenameSheet: View {
    let currentName: String
    let onSave: (String) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var draft: String
    @State private var saving = false
    @State private var error: String?
    @FocusState private var focused: Bool

    init(currentName: String, onSave: @escaping (String) async throws -> Void) {
        self.currentName = currentName
        self.onSave = onSave
        _draft = State(initialValue: currentName)
    }

    private var trimmed: String {
        draft.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("device.renameTitle", text: $draft)
                        .focused($focused)
                        .submitLabel(.done)
                        .onSubmit(save)
                        .disabled(saving)
                } footer: {
                    Text("device.renameHint")
                }

                if let error {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(Theme.danger)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("device.renameTitle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("device.cancel") { dismiss() }
                        .disabled(saving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("device.save", action: save)
                        .disabled(saving || trimmed.isEmpty || trimmed == currentName)
                }
            }
            .onAppear { focused = true }
        }
        .tint(Theme.charge)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    private func save() {
        guard !trimmed.isEmpty, !saving else { return }
        error = nil
        saving = true
        Task {
            defer { saving = false }
            do {
                try await onSave(trimmed)
                dismiss()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}
