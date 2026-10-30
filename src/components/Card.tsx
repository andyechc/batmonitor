import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { colors, radius } from '@/theme';

export function Card({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <View style={styles.card}>
      {title && <Text style={styles.title}>{title}</Text>}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 18,
    gap: 14,
  },
  title: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
});
