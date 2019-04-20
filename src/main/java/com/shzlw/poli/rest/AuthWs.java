package com.shzlw.poli.rest;

import com.shzlw.poli.dao.UserDao;
import com.shzlw.poli.dto.LoginResponse;
import com.shzlw.poli.model.User;
import com.shzlw.poli.service.UserService;
import com.shzlw.poli.util.Constants;
import com.shzlw.poli.util.PasswordUtil;
import org.apache.tomcat.util.bcel.Const;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/auth")
public class AuthWs {

    @Autowired
    UserDao userDao;

    @Autowired
    UserService userService;

    @RequestMapping(value="/login/user", method = RequestMethod.POST)
    @Transactional
    public LoginResponse loginByUser(@RequestBody User user, HttpServletResponse response) {
        String username = user.getUsername();
        String password = user.getPassword();

        boolean isTempPassword = false;
        User existUser = userDao.findByUsernameAndPassword(username, password);
        if (existUser == null) {
            existUser = userDao.findByUsernameAndTempPassword(username, password);
            if (existUser == null) {
                return LoginResponse.ofError("Invalid username or password");
            } else {
                isTempPassword = true;
            }
        }

        String sessionKey = PasswordUtil.getUniqueId();
        userService.newOrUpdateSessionUserCache(user, sessionKey);
        userDao.updateSessionKey(existUser.getId(), sessionKey);

        Cookie sessionKeyCookie = new Cookie(Constants.SESSION_KEY, sessionKey);
        sessionKeyCookie.setMaxAge(Constants.COOKIE_TIMEOUT);
        sessionKeyCookie.setPath("/");
        response.addCookie(sessionKeyCookie);

        return LoginResponse.ofSucess(existUser.getUsername(), existUser.getSysRole(), isTempPassword);
    }

    @RequestMapping(value="/login/cookie", method= RequestMethod.POST)
    @Transactional
    public LoginResponse loginBySessionKey(@CookieValue(value = Constants.SESSION_KEY, defaultValue = "") String sessionKey) {
        if (sessionKey.isEmpty()) {
            return LoginResponse.ofError("Invalid username or password");
        }

        User user = userDao.findBySessionKey(sessionKey);
        if (user == null) {
            return LoginResponse.ofError("Invalid username or password");
        }

        userService.newOrUpdateSessionUserCache(user, sessionKey);
        return LoginResponse.ofSucess(user.getUsername(), user.getSysRole(), false);
    }

    @RequestMapping(value="/logout", method= RequestMethod.GET)
    @Transactional
    public void logout(@CookieValue(Constants.SESSION_KEY) String sessionKey, HttpServletResponse response) throws IOException {
        User user = userDao.findBySessionKey(sessionKey);
        if (user != null) {
            userService.removeFromSessionCache(sessionKey);
            userDao.updateSessionKey(user.getId(), null);

            Cookie sessionKeyCookie = new Cookie(Constants.SESSION_KEY, "");
            sessionKeyCookie.setMaxAge(0);
            sessionKeyCookie.setPath("/");
            response.addCookie(sessionKeyCookie);
        }
    }

    @RequestMapping(value="/login/changepassword", method= RequestMethod.POST)
    @Transactional
    public void changeTempPassword(@CookieValue(value = Constants.SESSION_KEY, defaultValue = "") String sessionKey,
        @RequestBody User user) {

        User existUser = userDao.findBySessionKey(sessionKey);
        if (user != null) {
            userDao.updateTempPassword(existUser.getId(), user.getPassword());
        }
    }
}
