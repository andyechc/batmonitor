import { router, Stack } from 'expo-router';
import { Image, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { BatteryHigh, CaretRight, Plus } from 'phosphor-react-native';

import { colors, radius } from '@/theme';
import { type SavedBattery, useSavedBatteries } from '@/lib/devices';
import { t } from '@/lib/i18n';

export default function HomeScreen() {
  const batteries = useSavedBatteries();

  return (
    <View style={styles.screen}>
      <Stack.Screen
        options={{
          headerRight: () => (
            <Pressable
              onPress={() => router.push('/add-device')}
              hitSlop={12}
              accessibilityLabel={t('home.add')}>
              <Plus size={24} color={colors.charge} weight="bold" />
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
        <BatteryHigh size={26} color={colors.charge} weight="duotone" />
      </View>
      <View style={styles.cardText}>
        <Text style={styles.cardName}>{battery.name}</Text>
        <Text style={styles.cardId}>{battery.id}</Text>
      </View>
      <CaretRight size={20} color={colors.muted} weight="bold" />
    </Pressable>
  );
}

function EmptyState() {
  return (
    <View style={styles.empty}>
      <Image source={require('../../assets/splash-icon.png')} style={styles.logo} />
      <Text style={styles.emptyTitle}>{t('home.empty.title')}</Text>
      <Text style={styles.emptyText}>{t('home.empty.text')}</Text>
      <Pressable
        onPress={() => router.push('/add-device')}
        style={({ pressed }) => [styles.primaryButton, pressed && styles.buttonPressed]}>
        <Plus size={20} color={colors.bg} weight="bold" />
        <Text style={styles.primaryButtonText}>{t('home.add')}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
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
  cardIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: colors.surfaceHigh,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardText: { flex: 1, gap: 4 },
  cardName: { color: colors.text, fontSize: 17, fontWeight: '600' },
  cardId: { color: colors.muted, fontSize: 13, fontVariant: ['tabular-nums'] },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32, gap: 14 },
  logo: { width: 180, height: 180, marginBottom: 8 },
  emptyTitle: { color: colors.text, fontSize: 22, fontWeight: '700' },
  emptyText: { color: colors.muted, fontSize: 15, textAlign: 'center', lineHeight: 22 },
  primaryButton: {
    marginTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: colors.charge,
    borderRadius: radius.pill,
    paddingVertical: 14,
    paddingHorizontal: 24,
  },
  buttonPressed: { opacity: 0.85 },
  primaryButtonText: { color: colors.bg, fontSize: 16, fontWeight: '700' },
});
