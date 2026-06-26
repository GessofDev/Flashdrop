// routes/userRoutes.js
const userController = require('../controllers/usersController');

module.exports = (app, upload) => {
  // Campo de archivo: 'photo'
  app.post('/api/auth/register', upload.single('photo'), userController.register);
  app.post('/api/auth/login', userController.login);
};
