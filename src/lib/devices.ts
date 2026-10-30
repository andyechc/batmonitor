import AsyncStorage from '@react-native-async-storage/async-storage';
import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';

export type SavedBattery = { id: string; name: string; addedAt: number };

const STORAGE_KEY = 'batmonitor.batteries.v1';

export async function getSavedBatteries(): Promise<SavedBattery[]> {
  const raw = await AsyncStorage.getItem(STORAGE_KEY);
  return raw ? (JSON.parse(raw) as SavedBattery[]) : [];
}

export async function addSavedBattery(battery: Pick<SavedBattery, 'id' | 'name'>): Promise<void> {
  const batteries = await getSavedBatteries();
  if (batteries.some((item) => item.id === battery.id)) return;
  batteries.push({ ...battery, addedAt: Date.now() });
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(batteries));
}

export async function removeSavedBattery(id: string): Promise<void> {
  const batteries = await getSavedBatteries();
  await AsyncStorage.setItem(
    STORAGE_KEY,
    JSON.stringify(batteries.filter((item) => item.id !== id)),
  );
}

export function useSavedBatteries() {
  const [batteries, setBatteries] = useState<SavedBattery[] | null>(null);

  useFocusEffect(
    useCallback(() => {
      getSavedBatteries().then(setBatteries);
    }, []),
  );

  return batteries;
}
