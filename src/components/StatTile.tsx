import { StyleSheet, Text, View } from 'react-native';

import { colors, radius } from '@/theme';

type Props = { label: string; value: string; unit?: string; accent?: string };

export function StatTile({ label, value, unit, accent = colors.text }: Props) {
  return (
    <View style={styles.tile}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.row}>
        <Text style={[styles.value, { color: accent }]}>{value}</Text>
        {unit && <Text style={styles.unit}>{unit}</Text>}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  tile: {
    flexBasis: '48%',
    flexGrow: 1,
    backgroundColor: colors.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
    gap: 6,
  },
  label: { color: colors.muted, fontSize: 13 },
  row: { flexDirection: 'row', alignItems: 'baseline', gap: 4 },
  value: { fontSize: 26, fontWeight: '700', fontVariant: ['tabular-nums'] },
  unit: { color: colors.muted, fontSize: 14 },
});
