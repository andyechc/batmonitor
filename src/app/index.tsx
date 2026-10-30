import { router, Stack } from 'expo-router';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, radius } from '@/theme';
import { type SavedBattery, useSavedBatteries } from '@/lib/devices';

export default function HomeScreen() {
  const batteries = useSavedBatteries();

  return (
    <View style={styles.screen}>
      <Stack.Screen
        options={{
          headerRight: () => (
            <Pressable onPress={() => router.push('/add-device')} hitSlop={12}>
              <Text style={styles.addButton}>Añadir</Text>
            </Pressable>
          ),
        }}
      />
      {batteries !== null && batteries.length === 0 && <EmptyState />}
      {batteries !== null && batteries.length > 0 && (
        <FlatList
          data={batteries}
          keyExtractor={(battery) => battery.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => <BatteryCard battery={item} />}
        />
      )}
    </View>
  );
}

function BatteryCard({ battery }: { battery: SavedBattery }) {
  return (
    <Pressable
      onPress={() => router.push({ pathname: '/device/[id]', params: { id: battery.id } })}
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
      <View style={styles.cardIcon}>
        <View style={styles.cardIconCap} />
        <View style={styles.cardIconBody} />
      </View>
      <View style={styles.cardText}>
        <Text style={styles.cardName}>{battery.name}</Text>
        <Text style={styles.cardId}>{battery.id}</Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </Pressable>
  );
}

function EmptyState() {
  return (
    <View style={styles.empty}>
      <View style={styles.emptyIcon}>
        <View style={styles.emptyIconBody} />
      </View>
      <Text style={styles.emptyTitle}>Sin baterías</Text>
      <Text style={styles.emptyText}>
        Añade tu BMS por Bluetooth para ver su estado en tiempo real. No necesitas cuenta.
      </Text>
      <Pressable
        onPress={() => router.push('/add-device')}
        style={({ pressed }) => [styles.primaryButton, pressed && styles.buttonPressed]}>
        <Text style={styles.primaryButtonText}>Añadir batería</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  addButton: { color: colors.charge, fontSize: 16, fontWeight: '600' },
  list: { padding: 16, gap: 12 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    backgroundColor: colors.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
  },
  cardPressed: { backgroundColor: colors.surfaceHigh },
  cardIcon: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  cardIconCap: {
    width: 10,
    height: 4,
    borderTopLeftRadius: 2,
    borderTopRightRadius: 2,
    backgroundColor: colors.charge,
  },
  cardIconBody: {
    width: 26,
    height: 30,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: colors.charge,
  },
  cardText: { flex: 1, gap: 4 },
  cardName: { color: colors.text, fontSize: 17, fontWeight: '600' },
  cardId: { color: colors.muted, fontSize: 13, fontVariant: ['tabular-nums'] },
  chevron: { color: colors.muted, fontSize: 26 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32, gap: 14 },
  emptyIcon: {
    width: 96,
    height: 96,
    borderRadius: 48,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  emptyIconBody: {
    width: 36,
    height: 44,
    borderRadius: 8,
    borderWidth: 3,
    borderColor: colors.charge,
  },
  emptyTitle: { color: colors.text, fontSize: 22, fontWeight: '700' },
  emptyText: { color: colors.muted, fontSize: 15, textAlign: 'center', lineHeight: 22 },
  primaryButton: {
    marginTop: 12,
    backgroundColor: colors.charge,
    borderRadius: radius.pill,
    paddingVertical: 14,
    paddingHorizontal: 28,
  },
  buttonPressed: { opacity: 0.85 },
  primaryButtonText: { color: colors.bg, fontSize: 16, fontWeight: '700' },
});
