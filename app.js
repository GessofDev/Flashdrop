require('dotenv').config();

const express = require('express');
const path = require('path');
const logger = require('morgan');
const cors = require('cors');
const passport = require('passport');
const multer = require('multer');

const app = express();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 }
});

app.use(logger(process.env.NODE_ENV === 'production' ? 'combined' : 'dev'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cors({ origin: true }));
app.disable('x-powered-by');

app.use(passport.initialize());
require('./config/passport')(passport);

require('./routes/userRoutes')(app, upload);
require('./routes/appRoutes')(app);

const adminPath = path.join(__dirname, 'public', 'admin');
app.use('/admin', express.static(adminPath));
app.get('/admin', (_, res) => res.sendFile(path.join(adminPath, 'index.html')));

const { uploadFile } = require('./utils/storage_supabase');
app.post('/api/_test/upload', upload.single('image'), async (req, res) => {
  try {
    if (!req.file) return res.status(400).json({ ok: false, msg: 'Falta campo image' });
    const uploaded = await uploadFile(req.file.buffer, req.file.originalname, 'tests');
    return res.json({ ok: true, url: uploaded.publicUrl });
  } catch (error) {
    return res.status(500).json({ ok: false, error: error.message });
  }
});

app.get('/health', (_, res) => res.json({ ok: true, database: 'supabase' }));
app.get('/', (_, res) => res.redirect('/admin'));

app.use((error, req, res, next) => {
  console.error(error);
  return res.status(error.status || 500).json({ error: error.message || 'Error interno' });
});

module.exports = app;
