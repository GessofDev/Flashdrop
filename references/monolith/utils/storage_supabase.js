// utils/storage_supabase.js
const supabase = require('../config/supabase');
const path = require('path');
const { v4: uuid } = require('uuid');

/**
 * Sube un archivo a Supabase Storage y retorna { path, publicUrl }
 * @param {Buffer} fileBuffer - contenido del archivo en memoria
 * @param {string} originalName - nombre original para extraer extensión
 * @param {string} folder - subcarpeta lógica (ej: 'users')
 */
async function uploadFile(fileBuffer, originalName, folder = 'users') {
  const ext = path.extname(originalName || '').toLowerCase() || '.bin';
  const fileName = `${folder}/${uuid()}${ext}`;

  const { error: uploadError } = await supabase
    .storage
    .from(process.env.SUPABASE_BUCKET)
    .upload(fileName, fileBuffer, {
      contentType: mimeFromExt(ext),
      cacheControl: '3600',
      upsert: false
    });

  if (uploadError) throw uploadError;

  const { data } = supabase
    .storage
    .from(process.env.SUPABASE_BUCKET)
    .getPublicUrl(fileName);

  return { path: fileName, publicUrl: data.publicUrl };
}

function mimeFromExt(ext) {
  switch (ext) {
    case '.jpg':
    case '.jpeg': return 'image/jpeg';
    case '.png': return 'image/png';
    case '.webp': return 'image/webp';
    case '.gif': return 'image/gif';
    default: return 'application/octet-stream';
  }
}

module.exports = { supabase, uploadFile };
