package project.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Forum {
    private final String slug;
    private  String user;
    private final String title;
    private final int posts;
    private final int threads;

    public Forum() {
        this.slug = "f";
        this.user = "f";
        this.title = "f";
        this.posts = -1;
        this.threads = -1;
    }


    @JsonCreator
    public Forum(
            @JsonProperty("slug") String slug,
            @JsonProperty("user") String user,
            @JsonProperty("title") String title,
            @JsonProperty("posts") int postCount,
            @JsonProperty("threads") int threadCount
    ) {
        this.slug = slug;
        this.posts = postCount;
        this.threads = threadCount;
        this.title = title;
        this.user = user;
    }

    public String getSlug() {
        return slug;
    }

    public String getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public int getPosts() {
        return posts;
    }

    public int getThreads() {
        return threads;
    }
    public void setUser(String user) {
        this.user = user;
    }

}
