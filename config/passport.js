const JwtStrategy = require('passport-jwt').Strategy;
const ExtractJwt = require('passport-jwt').ExtractJwt;
const Keys = require('./keys');
const User = require('../models/user');

module.exports = passport => {
  passport.use(new JwtStrategy({
    jwtFromRequest: ExtractJwt.fromAuthHeaderWithScheme('jwt'),
    secretOrKey: Keys.secretOrKey
  }, async (jwtPayload, done) => {
    try {
      const user = await User.findById(jwtPayload.id);
      return done(null, user || false);
    } catch (error) {
      return done(error, false);
    }
  }));
};
