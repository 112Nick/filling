package project.DAO;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import project.models.Post;
import project.models.Thread;
import project.models.User;
import project.models.Vote;
import project.utils.Response;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
public class ThreadDAO {
    private final JdbcTemplate template;
    private final PostDAO postDAO;
    private final UserDAO userDAO;

    public ThreadDAO(JdbcTemplate template, PostDAO postDAO, UserDAO userDAO) {
        this.template = template;
        this.postDAO = postDAO;
        this.userDAO = userDAO;

    }

    public Integer count() {
        return template.queryForObject(
                "SELECT COUNT(*) FROM thread;",
                new Object[]{}, Integer.class);
    }

    public Response<Vote> getVote (String nickname, int threadID) {
        Response<Vote> result = new Response<>();
        try {
            final Vote vt =  template.queryForObject(
                    "SELECT * FROM vote WHERE LOWER(nickname) = LOWER(?) AND threadID = ?",
                    new Object[]{nickname, threadID},  Mappers.voteMapper);
            result.setResponse(vt, HttpStatus.OK);
            return result;
        }
        catch (DataAccessException e) {
            result.setResponse(new Vote(), HttpStatus.NOT_FOUND);
            return result;
        }
    }
    //api/thread/649/posts?limit=16&since=248156&sort=flat
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<List<Post>> getPosts(Thread thrd, Integer limit, Integer since, String sort, Boolean desc) {
        List<Object> tempObj = new ArrayList<>();
        Integer[] myarr2 = new Integer[2];
        final StringBuilder postQuery = new StringBuilder( "SELECT * FROM post WHERE thread = ? " );
        tempObj.add(thrd.getId());
        myarr2[0] = thrd.getId();
        if (sort == null) {
            sort = "flat";
        }
        Response<List<Post>> result = new Response<>();
        List<Post> posts = new ArrayList<>();
        switch (sort) {

            case "flat":
                if (since != null) {
                    if (desc != null && desc) {
                        postQuery.append(" AND id < ? ");
                    } else {
                        postQuery.append(" AND id > ? ");
                    }
                    tempObj.add(since);
                }
                postQuery.append(" ORDER BY created ");
                if (desc != null && desc) {
                    postQuery.append(" DESC, id DESC ");
                }
                else {
                    postQuery.append(" , id ");

                }
                if (limit != null) {
                    postQuery.append(" LIMIT ? ");
                    tempObj.add(limit);
                }
                break;

            case "tree":
                if (since != null) {
                    if (desc != null && desc) {
                        postQuery.append(" AND path < (SELECT path FROM post WHERE id = ?) ");
                    } else {
                        postQuery.append(" AND path > (SELECT path FROM post WHERE id = ?) ");
                    }
                    tempObj.add(since);
                }
                postQuery.append(" ORDER BY path ");
                if (desc != null && desc) {
                    postQuery.append(" DESC, id DESC ");
                }
                else {
                    postQuery.append(" , id ");
                }
                if (limit != null) {
                    postQuery.append (" LIMIT ?");
                    tempObj.add(limit);
                }
                break;

            case "parent_tree":
                Response<List<Post>> parentsArray = getParents(thrd, since, limit, desc);

                if (parentsArray.getStatus() == HttpStatus.NOT_FOUND) {
                    return parentsArray;
                }
                else {
                        postQuery.append("AND path[1] = ?");
                        postQuery.append(" ORDER BY path ");
                        if (desc != null && desc) {
                            postQuery.append(" DESC, id DESC ");
                        }
                        else {
                            postQuery.append(" , id ");
                        }
                    try {
                            for (Post cur : parentsArray.getBody()) {
                                myarr2[1] = cur.getId();
                                posts.addAll(template.query(postQuery.toString(),
                                        myarr2, Mappers.postMapper));
                            }
                            result.setResponse(posts, HttpStatus.OK);
                            return result;
                        } catch (DataAccessException e) {
                        result.setResponse(posts, HttpStatus.NOT_FOUND);
                            return result;
                        }

                }

        }

        try {
            posts = template.query(postQuery.toString(),
                    tempObj.toArray(), Mappers.postMapper);
            result.setResponse(posts, HttpStatus.OK);
            return result;
        } catch (DataAccessException e) {
            result.setResponse(posts, HttpStatus.NOT_FOUND);
            return result;
        }
    }

    private Response<List<Post>> getParents(Thread thrd, Integer since, Integer limit, Boolean desc) {
        List<Object> tempObj = new ArrayList<>();

        final StringBuilder postQuery = new StringBuilder( "SELECT * FROM post WHERE thread = ? AND parent = 0 " );
        tempObj.add(thrd.getId());

        if (since != null) {
            if (desc != null && desc) {
                postQuery.append(" AND path[1] < (SELECT path[1] FROM post WHERE id = ?)  ");
            } else {
                postQuery.append(" AND path[1] > (SELECT path[1] FROM post WHERE id = ?)  ");
            }
            tempObj.add(since);
        }
        postQuery.append("ORDER BY id ");
        if (desc != null && desc) {
            postQuery.append(" DESC ") ;
        }
        if (limit != null) {
            postQuery.append(" LIMIT ? ");
            tempObj.add(limit);

        }
        final List<Post> res = template.query(
                postQuery.toString(),
                tempObj.toArray(), Mappers.postMapper);

        Response<List<Post>> result = new Response<>();
        if (res.isEmpty()) {
            result.setResponse(res, HttpStatus.OK);
            return result;
        }
        result.setResponse(res, HttpStatus.OK);
        return result;
    }

    //@Transactional(isolation = Isolation.READ_COMMITTED)
    private void threadsInc(String slug) {

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            template.update(con -> {
                PreparedStatement statement = con.prepareStatement(
                        "UPDATE forum SET threads = threads + 1 WHERE LOWER (slug) = lower(?)",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                statement.setString(1, slug);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException e) {

        } catch (DataAccessException e ) {

        }
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<Thread> createThread(Thread body) {
        Response<Thread> result = new Response<>();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            template.update(con -> {
                PreparedStatement statement = con.prepareStatement(
                        "INSERT INTO thread(votes, slug, author, forum, title, message, created, forumid)"
                                + " VALUES(?,?,?,?,?,?,?::TIMESTAMPTZ,?) "+" returning id",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                statement.setInt(1, body.getVotes());
                statement.setString(2, body.getSlug());
                statement.setString(3, body.getAuthor());
                statement.setString(4, body.getForum());
                statement.setString(5, body.getTitle());
                statement.setString(6, body.getMessage());
                statement.setString(7, body.getCreated());
                statement.setInt(8, body.getForumID());

                return statement;
            }, keyHolder);
            threadsInc(body.getForum());

            body.setId(keyHolder.getKey().intValue()); // set Id
            result.setResponse(body, HttpStatus.CREATED);
            return result;

        } catch (DuplicateKeyException e) {
            result.setResponse(new Thread(), HttpStatus.CONFLICT);
            return result;

        } catch (DataAccessException e) {
            result.setResponse(new Thread(), HttpStatus.NOT_FOUND);
            return result;
        }
    }

    public Response<Thread> getThread(String slug) {
        Response<Thread> result = new Response<>();
        try {
            final Thread t= template.queryForObject(
                    "SELECT * FROM thread WHERE LOWER(slug) = LOWER(?)",
                    new Object[]{slug},  Mappers.threadMapper);
            result.setResponse(t, HttpStatus.OK);
            return result;
        } catch (DataAccessException e) {
            result.setResponse(new Thread(), HttpStatus.NOT_FOUND);
            return result;
        }

    }

    public Response<Thread> getThreadById(int id) {
        Response<Thread> result = new Response<>();
        try {
            final Thread t= template.queryForObject(
                    "SELECT * FROM thread WHERE id = ?",
                    new Object[]{id},  Mappers.threadMapper);
            result.setResponse(t, HttpStatus.OK);
            return result;
        } catch (DataAccessException e) {
            result.setResponse(new Thread(), HttpStatus.NOT_FOUND);
            return result;
        }

    }

    public Response<Thread> getThreadBySLugOrId(String slug_or_id) {
        int id = -1;
        try {
            id = Integer.parseInt(slug_or_id);
        } catch (java.lang.NumberFormatException e ) {

        }
        Response<Thread> res;
        if (id != -1) {
           res = getThreadById(id);

        }
        else {
            res = getThread(slug_or_id);
        }
        if (res.getStatus() == HttpStatus.NOT_FOUND) {
            res.setResponse(new Thread(), HttpStatus.NOT_FOUND);
            return res;
        }
        return res;
    }

    public Response<List<Thread>> getThreadByForum(String forum) {
        final List<Thread> res = template.query(
                "SELECT * FROM thread WHERE lower(forum) = lower(?)",
                new Object[]{forum}, Mappers.threadMapper);
        Response<List<Thread>> result = new Response<>();

        if (res.isEmpty()) {
            result.setResponse(res, HttpStatus.NOT_FOUND);
            return result;
        }
        result.setResponse(res, HttpStatus.OK);
        return result;
    }


    public Response<List<Thread>> getThreads(int forumID, Integer limit, String since, Boolean desc) {
        List<Object> tempObj = new ArrayList<>();
        final StringBuilder postQuery = new StringBuilder(
                "SELECT * FROM thread WHERE forumID = ? ");
        tempObj.add(forumID);
        if (since != null) {
            if (desc != null && desc) {
                postQuery.append("AND created <= ?::timestamptz "); // ::timestamptz
            } else {
                postQuery.append("AND created >= ?::timestamptz ");
            }
            tempObj.add(since);
        }
        postQuery.append("ORDER BY created ");
        if (desc != null && desc) {
            postQuery.append("DESC ");
        }
        if (limit != null) {
            postQuery.append("LIMIT ?");
            tempObj.add(limit);
        }
        Response<List<Thread>> result = new Response<>();
        List<Thread> threads1 = new ArrayList<>();
        try {
            threads1 = template.query(postQuery.toString(),
                    tempObj.toArray(), Mappers.threadMapper);
            result.setResponse(threads1, HttpStatus.OK);
            return result;
        } catch (DataAccessException e) {
            result.setResponse(threads1, HttpStatus.NOT_FOUND);
            return result;
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<Vote> vote (String nickname,int threadID, int voice, String forum) {

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            template.update(con -> {
            PreparedStatement statement = con.prepareStatement(
                    "INSERT INTO vote(nickname, threadID, voice, forum)"
                            + " VALUES (?,?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);
            statement.setString(1, nickname);
            statement.setInt(2, threadID);
            statement.setInt(3, voice);
            statement.setString(4, forum);
            return statement;
        }, keyHolder);

            return new Response<>(new Vote(nickname,voice), HttpStatus.CREATED);
        } catch(DuplicateKeyException e){
                Response<Vote> res = new Response<>();
                res.setResponse(new Vote(), HttpStatus.CONFLICT);
                return res;
        }

    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<Thread> updateThreadVoice (Thread thread, int voice, boolean revotes) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        if (revotes) {
            voice *= 2;
        }
        int threadID = thread.getId();
        int cur = thread.getVotes();
        int newVoice = cur + voice;
        try {
            template.update(con -> {
                PreparedStatement statement = con.prepareStatement(
                        "UPDATE thread SET votes = ? WHERE id = ?",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                statement.setInt(1, newVoice);
                statement.setInt(2, threadID);
                return statement;
            }, keyHolder);

            Response<Thread> res = new Response<>();
            res.setResponse(thread, HttpStatus.OK);
            return res;
        } catch(DuplicateKeyException e){
            Response<Thread> res = new Response<>();
            res.setResponse(new Thread(), HttpStatus.CONFLICT);
            return res;
        }

    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<Thread> updateThread (Thread thread, int id) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            template.update(con -> {
                PreparedStatement statement = con.prepareStatement(
                        "UPDATE thread SET title = COALESCE (?, title)," +
                                " message = COALESCE (?,message)  WHERE id = ?",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                statement.setString(1, thread.getTitle());
                statement.setString(2, thread.getMessage());
                statement.setInt(3, id);
                return statement;
            }, keyHolder);

            Response<Thread> res = new Response<>();
            res.setResponse(thread, HttpStatus.OK);
            return res;
        } catch(DuplicateKeyException e){
            Response<Thread> res = new Response<>();
            res.setResponse(new Thread(), HttpStatus.CONFLICT);
            return res;
        }

    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<Vote> reVote (String nickname,int threadID, int voice) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            template.update(con -> {
                PreparedStatement statement = con.prepareStatement(
                        "UPDATE vote SET voice = ? WHERE LOWER(nickname) = LOWER(?)" +
                                "AND threadID = ?",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                statement.setInt(1, voice);
                statement.setString(2, nickname);
                statement.setInt(3, threadID);
                return statement;
            }, keyHolder);

            return new Response<>(new Vote(nickname,voice), HttpStatus.OK);
        } catch(DuplicateKeyException e){
            Response<Vote> res = new Response<>();
            res.setResponse(new Vote(), HttpStatus.CONFLICT);
            return res;
        }

    }

    public void postsInc(String slug, int inc) {

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            template.update(con -> {
                PreparedStatement statement = con.prepareStatement(
                        "UPDATE forum SET posts = posts + ? WHERE LOWER(slug) = lower(?)",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                statement.setInt(1, inc);
                statement.setString(2, slug);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException e) {

        } catch (DataAccessException e) {

        }

    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response<List<Post>> createPosts(List<Post> posts) {

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            for (Post body: posts) {
                Response<User> userExists = userDAO.getUserByNick(body.getAuthor());
                if (userExists.getStatus() == HttpStatus.NOT_FOUND) {
                    Response<List<Post>> res = new Response<>();
                    res.setResponse(posts, HttpStatus.NOT_FOUND);
                    return res;
                }
                body.setCreated(posts.get(0).getCreated());
                Response<Post> parent = postDAO.getPostById(body.getParent());
                if (parent.getStatus() != HttpStatus.NOT_FOUND && parent.getBody().getThread() != body.getThread()) {
                    Response<List<Post>> res = new Response<>();
                    res.setResponse(posts, HttpStatus.CONFLICT);
                    return res;

                } else if(parent.getStatus() == HttpStatus.NOT_FOUND && body.getParent() != 0) {
                    Response<List<Post>> res = new Response<>();
                    res.setResponse(posts, HttpStatus.CONFLICT);
                    return res;
                }

                template.update(con -> {
                    PreparedStatement statement = con.prepareStatement(
                            "INSERT INTO post(parent, author, message, isEdited, forum, created, thread)"
                                    + " VALUES (?,?,?,?,?,?::timestamptz,?)" + " returning id",
                            PreparedStatement.RETURN_GENERATED_KEYS);
                    statement.setInt(1, body.getParent());
                    statement.setString(2, body.getAuthor());
                    statement.setString(3, body.getMessage());
                    statement.setBoolean(4, body.getIsEdited());
                    statement.setString(5, body.getForum());
                    statement.setString(6, body.getCreated());
                    statement.setInt(7, body.getThread());

                    return statement;
                }, keyHolder);
                body.setId(keyHolder.getKey().intValue());


                template.update(con -> {
                    ArrayList arr;
                    if (body.getParent() == 0) {
                        arr = new ArrayList<Object>(Arrays.asList(body.getId()));
                    }
                    else {
                        arr = new ArrayList<Object>(Arrays.asList(parent.getBody().getPath()));
                        arr.add(body.getId());
                    }
                    PreparedStatement statement = con.prepareStatement(
                            "UPDATE post SET path = ? WHERE id = ?",
                            PreparedStatement.RETURN_GENERATED_KEYS);
                    statement.setArray(1, con.createArrayOf("INT", arr.toArray()));
                    statement.setInt(2, body.getId());
                    return statement;
                }, keyHolder);
            }
            postsInc(posts.get(0).getForum(),posts.size());
            Response<List<Post>> res = new Response<>();
            res.setResponse(posts, HttpStatus.CREATED);
            return res;
        } catch (DuplicateKeyException e) {
            Response<List<Post>> res = new Response<>();
            res.setResponse(posts, HttpStatus.CONFLICT);
            return res;

        }
    }
}
