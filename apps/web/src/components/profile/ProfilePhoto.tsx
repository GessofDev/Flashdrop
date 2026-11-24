import { useCallback, useEffect, useRef, useState } from 'react';

import { asApiError, profilePhotoApi } from '@/api';
import type { ApiError } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Button } from '@/components/ui';

export interface ProfilePhotoProps {
  /**
   * Called with the photo the backend holds after an upload or a deletion.
   *
   * The backend owns the photo: the screen only forwards the url it answers.
   */
  onPhotoChange?: (photo: string | null) => void;
}

/**
 * Photo of the profile (spec S9).
 *
 * The backend already owns the three ends of the photo — upload, read and
 * delete — so this block asks the backend and shows what it answers:
 *
 * - the bytes travel with the session (`GET /auth/profile/photo` needs the
 *   `Authorization` header), so the picture is shown with an object URL;
 * - a user without a photo is not a failure: the backend answers 404 and the
 *   block simply shows no picture;
 * - a failed upload or deletion shows the literal message of the backend.
 *
 * The object URL is revoked when it is replaced and when the block goes
 * away, so the browser does not keep the bytes of a picture nobody shows.
 */
export function ProfilePhoto({ onPhotoChange }: ProfilePhotoProps) {
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selected, setSelected] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);

  const inputRef = useRef<HTMLInputElement | null>(null);
  const objectUrlRef = useRef<string | null>(null);

  /** Show the bytes of the photo and release the previous object URL. */
  const showPhoto = useCallback((blob: Blob | null) => {
    if (objectUrlRef.current !== null) {
      URL.revokeObjectURL(objectUrlRef.current);
      objectUrlRef.current = null;
    }

    const next = blob === null ? null : URL.createObjectURL(blob);

    objectUrlRef.current = next;
    setPhotoUrl(next);
  }, []);

  /** Ask the backend for the bytes of the photo again. */
  const reload = useCallback(async () => {
    showPhoto(await profilePhotoApi.read());
  }, [showPhoto]);

  useEffect(() => {
    let active = true;

    profilePhotoApi
      .read()
      .then((blob) => {
        if (active) {
          showPhoto(blob);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(asApiError(cause));
        }
      });

    return () => {
      active = false;

      if (objectUrlRef.current !== null) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }
    };
  }, [showPhoto]);

  const handleUpload = async () => {
    if (selected === null) {
      return;
    }

    setBusy(true);
    setError(null);
    setNotice(null);

    try {
      const updated = await profilePhotoApi.upload(selected);

      onPhotoChange?.(updated.photo);
      setSelected(null);

      if (inputRef.current !== null) {
        inputRef.current.value = '';
      }

      await reload();
      setNotice('Foto de perfil actualizada.');
    } catch (cause: unknown) {
      setError(asApiError(cause));
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async () => {
    setBusy(true);
    setError(null);
    setNotice(null);

    try {
      await profilePhotoApi.remove();

      showPhoto(null);
      onPhotoChange?.(null);
      setNotice('Foto de perfil eliminada.');
    } catch (cause: unknown) {
      setError(asApiError(cause));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section
      aria-labelledby="profile-photo-title"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-6 shadow-card"
    >
      <div className="flex flex-col gap-1">
        <h2 id="profile-photo-title" className="font-display text-lg font-bold text-foreground">
          Foto de perfil
        </h2>
        <p className="text-xs text-foreground-secondary">
          La foto se guarda en tu cuenta y aparece en tus pedidos.
        </p>
      </div>

      {photoUrl !== null ? (
        <img
          src={photoUrl}
          alt="Foto de perfil"
          className="h-24 w-24 rounded-full border border-border object-cover"
        />
      ) : null}

      <div className="flex flex-col gap-1.5">
        <label htmlFor="profile-photo-file" className="text-sm font-semibold text-foreground">
          Archivo de la foto
        </label>
        <input
          id="profile-photo-file"
          ref={inputRef}
          name="photo"
          type="file"
          accept="image/jpeg, image/png, image/webp"
          onChange={(event) => {
            setSelected(event.target.files?.[0] ?? null);
            setError(null);
            setNotice(null);
          }}
          className="text-sm text-foreground-secondary file:mr-3 file:rounded-control file:border-0 file:bg-canvas file:px-3 file:py-2 file:text-sm file:font-semibold file:text-foreground"
        />
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Button
          onClick={() => {
            void handleUpload();
          }}
          disabled={selected === null || busy}
        >
          {photoUrl === null ? 'Subir foto' : 'Reemplazar foto'}
        </Button>

        {photoUrl !== null ? (
          <Button
            variant="destructive"
            onClick={() => {
              void handleDelete();
            }}
            disabled={busy}
          >
            Quitar foto
          </Button>
        ) : null}
      </div>

      {notice !== null ? (
        <p role="status" className="text-sm font-semibold text-success">
          {notice}
        </p>
      ) : null}

      {error !== null ? <ApiErrorNotice error={error} /> : null}
    </section>
  );
}
