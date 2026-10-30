import { StyleSheet, Text, View } from 'react-native';

import { colors } from '@/theme';

export function CellBars({ cells }: { cells: number[] }) {
  const min = Math.min(...cells);
  const max = Math.max(...cells);
  const span = max - min;
  const balanced = span < 0.02;

  return (
    <View style={styles.list}>
      {cells.map((voltage, index) => {
        const ratio = balanced ? 1 : 0.2 + 0.8 * ((voltage - min) / span);
        const color = balanced
          ? colors.info
          : voltage === max
            ? colors.charge
            : voltage === min
              ? colors.discharge
              : colors.info;
        return (
          <View key={index} style={styles.row}>
            <Text style={styles.label}>C{index + 1}</Text>
            <View style={styles.track}>
              <View style={[styles.fill, { width: `${ratio * 100}%`, backgroundColor: color }]} />
            </View>
            <Text style={styles.value}>{voltage.toFixed(3)} V</Text>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: 10 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  label: { color: colors.muted, width: 28, fontSize: 13, fontVariant: ['tabular-nums'] },
  track: {
    flex: 1,
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.surfaceHigh,
    overflow: 'hidden',
  },
  fill: { height: '100%', borderRadius: 5 },
  value: {
    color: colors.text,
    width: 84,
    textAlign: 'right',
    fontSize: 14,
    fontVariant: ['tabular-nums'],
  },
});
