const User = require('../models/user');
const Rol = require('../models/rol');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const keys = require('../config/keys');
const { uploadFile } = require('../utils/storage_supabase');

module.exports = {
  async login(req, res) {
    try {
      const { login, password } = req.body || {};
      if (!login || !password) {
        return res.status(400).json({ success: false, message: 'Email y password son obligatorios' });
      }

      const user = await User.findByEmail(login);
      if (!user) return res.status(401).json({ success: false, message: 'El email no fue encontrado' });

      const valid = await bcrypt.compare(password, user.passwordHash || '');
      if (!valid) return res.status(401).json({ success: false, message: 'Contrasena incorrecta' });

      const roleNames = user.roles.map(role => role.name);
      const token = jwt.sign(
        { id: user.id, email: user.email, roles: roleNames },
        keys.secretOrKey,
        { expiresIn: '8h' }
      );

      return res.json({
        success: true,
        message: 'Usuario autenticado correctamente',
        data: {
          id: `${user.id}`,
          name: user.name,
          lastName: user.lastName,
          email: user.email,
          phone: user.phone,
          photo: user.photo,
          rut: user.rut,
          roles: user.roles,
          session_token: `JWT ${token}`
        }
      });
    } catch (error) {
      return res.status(500).json({ success: false, message: 'Error interno', error: error.message });
    }
  },

  async register(req, res) {
    try {
      const { email, rut, name, last_name, lastName, phone, password } = req.body || {};
      if (!email || !password) {
        return res.status(400).json({ success: false, message: 'Email y password son obligatorios' });
      }

      let photoUrl = null;
      if (req.file?.buffer) {
        const uploaded = await uploadFile(req.file.buffer, req.file.originalname, 'users');
        photoUrl = uploaded.publicUrl;
      }

      const userId = await User.create({
        email,
        rut,
        name,
        lastName: last_name ?? lastName ?? null,
        phone,
        photo: photoUrl,
        password
      });

      await Rol.create(userId, 1);
      return res.status(201).json({
        success: true,
        message: 'El registro se realizo correctamente',
        data: { id: userId, photo: photoUrl }
      });
    } catch (error) {
      const duplicate = error.code === '23505' || `${error.message}`.toLowerCase().includes('duplicate');
      return res.status(duplicate ? 409 : 400).json({
        success: false,
        message: duplicate ? 'Email o telefono ya registrado' : 'Error en registro',
        error: error.message
      });
    }
  }
};
