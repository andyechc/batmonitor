import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle } from 'react-native-svg';

import { colors } from '@/theme';

type Props = { soc: number; color: string; size?: number; stroke?: number };

export function SocRing({ soc, color, size = 200, stroke = 14 }: Props) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const progress = Math.min(100, Math.max(0, soc)) / 100;

  return (
    <View style={{ width: size, height: size }}>
      <Svg width={size} height={size} style={{ transform: [{ rotate: '-90deg' }] }}>
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          stroke={colors.surfaceHigh}
          strokeWidth={stroke}
          fill="none"
        />
        {progress > 0 && (
          <Circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke={color}
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={`${circumference * progress} ${circumference}`}
            fill="none"
          />
        )}
      </Svg>
      <View style={styles.center}>
        <Text style={[styles.value, { fontSize: size * 0.26 }]}>{Math.round(soc)}</Text>
        <Text style={styles.unit}>% carga</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center' },
  value: { color: colors.text, fontWeight: '700', fontVariant: ['tabular-nums'] },
  unit: { color: colors.muted, fontSize: 14, marginTop: -4 },
});
