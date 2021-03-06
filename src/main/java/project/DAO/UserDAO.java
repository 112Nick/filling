package project.DAO;

import java.security.AccessControlException;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import project.models.User;
import project.utils.Response;

@Service
//@Transactional
public class UserDAO {

    private final JdbcTemplate template;

    public UserDAO(JdbcTemplate template) {
        this.template = template;
    }

    public Integer count() {
        return template.queryForObject(
                "SELECT COUNT(*) FROM users;",
                new Object[]{}, Integer.class);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<User> createUser(User body)  {
        Response<User> result = new Response<>();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {
        template.update(con -> {
            PreparedStatement statement = con.prepareStatement(
                    "INSERT INTO users(fullname, nickname, email, about)" + " VALUES(?,?,?,?)" ,
                    PreparedStatement.RETURN_GENERATED_KEYS);
            statement.setString(1, body.getFullname());
            statement.setString(2, body.getNickname());
            statement.setString(3, body.getEmail());
            statement.setString(4, body.getAbout());
            return statement;
        }, keyHolder);
        result.setResponse(body, HttpStatus.CREATED);
        return result;
        }
        catch (DuplicateKeyException e) {
            result.setResponse(body, HttpStatus.CONFLICT);
            return result;
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<User> addUser(User body, int forumID)  {
        Response<User> result = new Response<>();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            template.update(con -> {
                PreparedStatement statement = con.prepareStatement(
                        "INSERT INTO forum_users(fullname, nickname, email, about, forumID)" + " VALUES(?,?,?,?,?)" ,
                        PreparedStatement.RETURN_GENERATED_KEYS);
                statement.setString(1, body.getFullname());
                statement.setString(2, body.getNickname());
                statement.setString(3, body.getEmail());
                statement.setString(4, body.getAbout());
                statement.setInt(5, forumID);
                return statement;
            }, keyHolder);
            result.setResponse(body, HttpStatus.CREATED);
            return result;
        }
        catch (DuplicateKeyException e) {
            result.setResponse(body, HttpStatus.CONFLICT);
            return result;
        }
    }

    public Response<User> getUserByNick(String nickname)  {
        Response<User> result = new Response<>();
        try {
            final User usr =  template.queryForObject(
                    "SELECT * FROM users WHERE nickname = ?::CITEXT",
                    new Object[]{nickname},  Mappers.userMapper);
            result.setResponse(usr, HttpStatus.OK);
            return result;
        }
        catch (DataAccessException e) {
            result.setResponse(new User(), HttpStatus.NOT_FOUND);
           return result;
        }
    }

    public List<Response<User>> getUserByNickOrEmail(String nickname, String email) { // TODO to one request
        List<Response<User>> resArr = new ArrayList<>();
        Response<User> res1 = getUserByNick(nickname);
        Response<User> res2 = getUserByEmail(email);
        if (res1.getStatus() == HttpStatus.OK && res2.getStatus() == HttpStatus.OK) {
            if (res1.equals(res2)) {
                resArr.add(res1);
            }
            else {
                resArr.add(res1);
                resArr.add(res2);
            }
        }
        else if (res1.getStatus() == HttpStatus.OK && res2.getStatus() == HttpStatus.NOT_FOUND) {
            resArr.add(res1);
        }
        else if (res1.getStatus() == HttpStatus.NOT_FOUND && res2.getStatus() == HttpStatus.OK) {
            resArr.add(res2);
        }
        else {
            resArr.clear();
        }
        return resArr;
    }

    public Response<User> getUserByEmail(String email) {
        Response<User> result = new Response<>();
        try {
            final User usr = template.queryForObject(
                    "SELECT * FROM users WHERE email = ?::CITEXT",
                    new Object[]{email},  Mappers.userMapper);
            result.setResponse(usr, HttpStatus.OK);
            return result;
        }
        catch (DataAccessException e) {
            result.setResponse(new User(), HttpStatus.NOT_FOUND);
            return result;
        }
    }

    public Response<List<User>> getUsers(int forumID, Integer limit, String since, Boolean desc) {

        List<Object> tempObj = new ArrayList<>();
        final StringBuilder postQuery = new StringBuilder(
                "SELECT nickname, fullname, email, about FROM forum_users WHERE forumID = ? ");
        tempObj.add(forumID);
        if (since != null) {
            if (desc != null && desc) {
                postQuery.append("AND nickname < ?::CITEXT ");
            } else {
                postQuery.append("AND nickname > ?::CITEXT ");
            }
            tempObj.add(since);
        }
        postQuery.append("ORDER BY nickname ");
        if (desc != null && desc) {
            postQuery.append("DESC ");
        }
        if (limit != null) {
            postQuery.append("LIMIT ?");
            tempObj.add(limit);
        }
        Response<List<User>> result = new Response<>();
        List<User> users = new ArrayList<>();
        try {
            users = template.query(postQuery.toString(),
                    tempObj.toArray(), Mappers.userMapper);
            result.setResponse(users, HttpStatus.OK);
            return result;
        } catch (DataAccessException e) {
            result.setResponse(users, HttpStatus.NOT_FOUND);
            //System.out.println("qweasdZX");
            return result;
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Response<User> updateUser(User body) {
        Response<User> result = new Response<>();

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {template.update(con -> {
            PreparedStatement statement = con.prepareStatement(
                    "UPDATE users SET " +
                            "fullname = COALESCE(?, fullname), " +
                            "about = COALESCE(?, about), " +
                            "email = COALESCE(?, email) " +
                            "WHERE LOWER(nickname) = LOWER(?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);
            statement.setString(1, body.getFullname());
            statement.setString(2, body.getAbout());
            statement.setString(3, body.getEmail());
            statement.setString(4, body.getNickname());
            return statement;
        }, keyHolder);
            result.setResponse(body,HttpStatus.OK);
            return result;
        } catch (DuplicateKeyException e) {
            result.setResponse(new User(), HttpStatus.CONFLICT);
            return result;
        }
        catch (AccessControlException e) {
            result.setResponse(new User(), HttpStatus.NOT_FOUND);
            return result;
        }
    }

}
