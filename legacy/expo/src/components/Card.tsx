import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { colors, radius } from '@/theme';
import type { IconComponent } from './icons';

type Props = { title?: string; icon?: IconComponent; children: ReactNode };

export function Card({ title, icon: Icon, children }: Props) {
  return (
    <View style={styles.card}>
      {title && (
        <View style={styles.titleRow}>
          {Icon && <Icon size={16} color={colors.muted} weight="duotone" />}
          <Text style={styles.title}>{title}</Text>
        </View>
      )}
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
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  title: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
});
