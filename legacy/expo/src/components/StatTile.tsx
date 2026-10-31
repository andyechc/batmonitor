import { StyleSheet, Text, View } from 'react-native';

import { colors, radius } from '@/theme';
import type { IconComponent } from './icons';

type Props = { label: string; value: string; unit?: string; icon: IconComponent; accent?: string };

export function StatTile({ label, value, unit, icon: Icon, accent = colors.text }: Props) {
  return (
    <View style={styles.tile}>
      <View style={styles.header}>
        <Icon size={18} color={accent} weight="duotone" />
        <Text style={styles.label}>{label}</Text>
      </View>
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
    gap: 8,
  },
  header: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  label: { color: colors.muted, fontSize: 13 },
  row: { flexDirection: 'row', alignItems: 'baseline', gap: 4 },
  value: { fontSize: 26, fontWeight: '700', fontVariant: ['tabular-nums'] },
  unit: { color: colors.muted, fontSize: 14 },
});
