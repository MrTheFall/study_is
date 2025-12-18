import { useEffect, useMemo, useState } from 'react';
import { authApi, clientsApi } from '@/api/client';
import { Client, GetCurrentUser200ResponseUserTypeEnum } from '@/api/generated/api';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { getApiErrorMessage } from '@/lib/apiError';
import { useAuthStore } from '@/store/authStore';

export function ProfilePage() {
  const { user, setAuth } = useAuthStore();

  const [client, setClient] = useState<Client | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileSaved, setProfileSaved] = useState(false);
  const [profile, setProfile] = useState({ name: '', phone: '', defaultAddress: '' });

  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSaved, setPasswordSaved] = useState(false);
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmNewPassword: '',
  });

  const clientId = user?.userId ?? null;

  useEffect(() => {
    void loadProfile();
  }, []);

  const loadProfile = async () => {
    try {
      setError(null);
      setLoading(true);

      let currentUser = useAuthStore.getState().user;
      if (!currentUser?.userId) {
        const me = await authApi.getCurrentUser();
        setAuth(useAuthStore.getState().token || '', me.data);
        currentUser = me.data;
      }

      if (!currentUser?.userId) {
        throw new Error('Не удалось определить пользователя');
      }

      if (currentUser.userType && currentUser.userType !== GetCurrentUser200ResponseUserTypeEnum.Client) {
        throw new Error('Доступно только клиенту');
      }

      const response = await clientsApi.getClientById(currentUser.userId);
      setClient(response.data);
      setProfile({
        name: response.data.name || '',
        phone: response.data.phone || '',
        defaultAddress: response.data.defaultAddress || '',
      });
    } catch (e: any) {
      console.error('Ошибка загрузки профиля:', e);
      setError(getApiErrorMessage(e, 'Не удалось загрузить профиль'));
      setClient(null);
    } finally {
      setLoading(false);
    }
  };

  const isDirty = useMemo(() => {
    if (!client) return false;
    return (
      (profile.name || '') !== (client.name || '') ||
      (profile.phone || '') !== (client.phone || '') ||
      (profile.defaultAddress || '') !== (client.defaultAddress || '')
    );
  }, [client, profile]);

  const saveProfile = async () => {
    if (!clientId) return;
    if (!profile.name.trim() || !profile.phone.trim()) {
      setProfileError('Имя и телефон обязательны');
      return;
    }

    setSavingProfile(true);
    try {
      setProfileError(null);
      setProfileSaved(false);
      const resp = await clientsApi.updateClient(clientId, {
        name: profile.name.trim(),
        phone: profile.phone.trim(),
        defaultAddress: profile.defaultAddress.trim() || undefined,
      });
      setClient(resp.data);
      setProfile({
        name: resp.data.name || '',
        phone: resp.data.phone || '',
        defaultAddress: resp.data.defaultAddress || '',
      });
      setProfileSaved(true);
      setTimeout(() => setProfileSaved(false), 2500);
    } catch (e: any) {
      console.error('Ошибка сохранения профиля:', e);
      setProfileError(getApiErrorMessage(e, 'Не удалось сохранить профиль'));
    } finally {
      setSavingProfile(false);
    }
  };

  const changePassword = async () => {
    if (!clientId) return;
    setPasswordSaved(false);

    if (!passwordForm.currentPassword.trim()) {
      setPasswordError('Введите текущий пароль');
      return;
    }
    if (!passwordForm.newPassword.trim()) {
      setPasswordError('Введите новый пароль');
      return;
    }
    if (passwordForm.newPassword.length < 6) {
      setPasswordError('Новый пароль должен быть не короче 6 символов');
      return;
    }
    if (passwordForm.newPassword !== passwordForm.confirmNewPassword) {
      setPasswordError('Подтверждение не совпадает');
      return;
    }

    setSavingPassword(true);
    try {
      setPasswordError(null);
      await clientsApi.changeClientPassword(clientId, {
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      });
      setPasswordForm({ currentPassword: '', newPassword: '', confirmNewPassword: '' });
      setPasswordSaved(true);
      setTimeout(() => setPasswordSaved(false), 2500);
    } catch (e: any) {
      console.error('Ошибка смены пароля:', e);
      setPasswordError(getApiErrorMessage(e, 'Не удалось сменить пароль'));
    } finally {
      setSavingPassword(false);
    }
  };

  if (loading) {
    return <LoadingState message="Загрузка профиля..." />;
  }

  if (error && !loading) {
    return (
      <div className="max-w-3xl mx-auto space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
          <div>
            <h1 className="text-3xl font-bold">Личный кабинет</h1>
            <p className="text-sm text-gray-500 mt-1">Профиль и безопасность</p>
          </div>
          <Button variant="outline" onClick={loadProfile}>
            Повторить
          </Button>
        </div>
        <Alert variant="error" title="Ошибка">
          {error}
        </Alert>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Личный кабинет</h1>
          <p className="text-sm text-gray-500 mt-1">Профиль и безопасность</p>
        </div>
        <Button variant="outline" onClick={loadProfile}>
          Обновить
        </Button>
      </div>

      <Card>
          <CardHeader>
            <CardTitle className="text-xl">Профиль</CardTitle>
            <CardDescription>Имя, телефон и адрес доставки по умолчанию</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Email</label>
              <Input value={client?.email || ''} readOnly />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Имя</label>
              <Input
                value={profile.name}
                onChange={(e) => setProfile((p) => ({ ...p, name: e.target.value }))}
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Телефон</label>
              <Input
                value={profile.phone}
                onChange={(e) => setProfile((p) => ({ ...p, phone: e.target.value }))}
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Адрес доставки</label>
              <Input
                value={profile.defaultAddress}
                onChange={(e) => setProfile((p) => ({ ...p, defaultAddress: e.target.value }))}
                placeholder="Например: Bikini Bottom, Coral St. 123"
              />
            </div>

            {profileError && <p className="text-sm text-red-600">{profileError}</p>}
            {profileSaved && <p className="text-sm text-green-700">Профиль обновлён</p>}

            <div className="flex justify-end">
              <Button onClick={saveProfile} disabled={savingProfile || !isDirty}>
                {savingProfile ? 'Сохранение…' : 'Сохранить изменения'}
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-xl">Смена пароля</CardTitle>
            <CardDescription>Требуется текущий пароль</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Input
              type="password"
              placeholder="Текущий пароль"
              value={passwordForm.currentPassword}
              onChange={(e) => setPasswordForm((p) => ({ ...p, currentPassword: e.target.value }))}
            />
            <Input
              type="password"
              placeholder="Новый пароль"
              value={passwordForm.newPassword}
              onChange={(e) => setPasswordForm((p) => ({ ...p, newPassword: e.target.value }))}
            />
            <Input
              type="password"
              placeholder="Повторите новый пароль"
              value={passwordForm.confirmNewPassword}
              onChange={(e) => setPasswordForm((p) => ({ ...p, confirmNewPassword: e.target.value }))}
            />

            {passwordError && <p className="text-sm text-red-600">{passwordError}</p>}
            {passwordSaved && <p className="text-sm text-green-700">Пароль изменён</p>}

            <div className="flex justify-end">
              <Button onClick={changePassword} disabled={savingPassword}>
                {savingPassword ? 'Сохранение…' : 'Сменить пароль'}
              </Button>
            </div>
          </CardContent>
        </Card>
    </div>
  );
}
