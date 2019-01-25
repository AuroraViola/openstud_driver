package lithium.openstud.driver.core;

import java.util.Objects;

public class News {
    private String imageUrl;
    private String title;
    private String url;
    private String locale;
    private String description;

    public String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    public String getLocale() {
        return locale;
    }

    void setLocale(String locale) {
        this.locale = locale;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "News{" +
                "imageUrl='" + imageUrl + '\'' +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", locale='" + locale + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        News news = (News) o;
        return Objects.equals(imageUrl, news.imageUrl) &&
                Objects.equals(title, news.title) &&
                Objects.equals(url, news.url) &&
                Objects.equals(locale, news.locale) &&
                Objects.equals(description, news.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageUrl, title, url, locale, description);
    }
}
