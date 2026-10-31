import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

import { t } from '@/lib/i18n';
import { colors } from '@/theme';

export default function RootLayout() {
  return (
    <>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.bg },
          headerTintColor: colors.text,
          headerShadowVisible: false,
          headerTitleStyle: { fontWeight: '600' },
          contentStyle: { backgroundColor: colors.bg },
        }}>
        <Stack.Screen name="index" options={{ title: t('app.home') }} />
        <Stack.Screen
          name="add-device"
          options={{ title: t('add.title'), presentation: 'modal' }}
        />
        <Stack.Screen name="device/[id]" options={{ title: t('ble.unnamed') }} />
      </Stack>
    </>
  );
}
