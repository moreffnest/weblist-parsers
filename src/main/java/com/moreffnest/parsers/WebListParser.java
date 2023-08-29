package com.moreffnest.parsers;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.InternetDomainName;
import com.moreffnest.parsers.exceptions.InvalidListPageException;
import com.moreffnest.parsers.exceptions.InvalidListTypeException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class WebListParser {
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss");

    public enum ListType {IMDB, KINOPOISK, MYANIMELIST, LETTERBOXD, SHIKIMORI, TRAKT, GOODREADS, YOUTUBE}

    public static Set<Title> parse(String url) throws InvalidListTypeException, InvalidListPageException {
        try {
            String host = new URI(url).getHost();
            String topDomain = InternetDomainName.from(host).topPrivateDomain().toString();
            ListType type = ListType.valueOf(
                    topDomain.substring(0, topDomain.lastIndexOf('.')).toUpperCase()
            );

            return switch (type) {
                case IMDB -> parseImdb(url);
                case KINOPOISK -> parseKinopoisk(url);
                case MYANIMELIST -> parseMyanimelist(url);
                case LETTERBOXD -> parseLetterboxd(url);
                case SHIKIMORI -> parseShikimori(url);
                case TRAKT -> parseTrakt(url);
                case GOODREADS -> parseGoodreads(url);
                default -> throw new InvalidListTypeException("Unexpected list type: " + type);
            };
        } catch (IOException | URISyntaxException | NullPointerException e) {
            throw new InvalidListPageException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new InvalidListTypeException(e.getMessage());
        }
    }

    public static Set<Title> mapYoutubeVideoToTitle(Set<YoutubeHistoryParser.YoutubeVideo> youtubeVideos) {
        return youtubeVideos.stream()
                .map(video -> {
                    return new Title(video.getTitle() + " (" + video.getChannelName() + ")",
                            video.getLink());
                }).collect(Collectors.toSet());
    }

    private static Set<Title> parseGoodreads(String url) throws IOException {
        Set<Title> resultSet = new HashSet<>();
        Document document = null;
        Element nextButton = null;
        do {
            document = Jsoup.connect(url).maxBodySize(0).get();
            nextButton = document.getElementsByClass("next_page").first();
            url = nextButton.attr("abs:href");

            Elements titles = document.getElementsByClass("bookTitle");
            resultSet.addAll(
                    titles.stream().map(link -> {
                                Title tempTitle = new Title();
                                tempTitle.setLink(link.attr("abs:href"));
                                tempTitle.setTitle(link.getElementsByTag("span").first().text());
                                return tempTitle;
                            })
                            .collect(Collectors.toSet())
            );
        } while (!nextButton.hasClass("disabled"));
        return resultSet;
    }

    private static Set<Title> parseTrakt(String url) throws IOException {
        Set<Title> resultSet = new HashSet<>();
        Document document = null;
        Element nextButton = null;
        do {
            document = Jsoup.connect(url).maxBodySize(0).get();
            nextButton = document.getElementsByClass("next").first();
            url = nextButton.getElementsByTag("a").first().attr("abs:href");

            Elements titles = document.getElementsByClass("titles");
            resultSet.addAll(
                    titles.stream().map(title -> title.getElementsByTag("a").first())
                            .map(link -> {
                                Title tempTitle = new Title();
                                tempTitle.setLink(link.attr("abs:href"));
                                tempTitle.setTitle(link.getElementsByTag("h3").first().text());
                                return tempTitle;
                            })
                            .collect(Collectors.toSet())
            );
        } while (!nextButton.hasClass("disabled"));
        return resultSet;
    }

    private static Set<Title> parseShikimori(String url) throws IOException {
        Set<Title> resultSet = new HashSet<>();
        Document document = Jsoup.connect(url).maxBodySize(0).get();

        Elements titles = document.getElementsByClass("tooltipped");
        resultSet.addAll(
                titles.stream().map(link -> {
                            Title tempTitle = new Title();
                            tempTitle.setLink(link.attr("abs:href"));
                            tempTitle.setTitle(link.getElementsByClass("name-en").first().text());
                            return tempTitle;
                        })
                        .collect(Collectors.toSet())
        );
        return resultSet;
    }

    private static Set<Title> parseKinopoisk(String url) throws IOException {
        Set<Title> resultSet = new HashSet<>();
        String nextPageButton = "»";
        url = url.split("#")[0] + "/perpage/200";
        Document document = null;
        while (url != null) {
            document = Jsoup.connect(url)
                    .maxBodySize(0).get();
            //if we encounter a kinopoisk's captcha then end parsing
            if (document.title().equals("Ой!") || document.title().equals("Oops!")) {
                return resultSet;
            }
            //getting next page url
            url = document.getElementsByClass("navigator").first()
                    .getElementsByTag("a").stream()
                    .filter(link -> link.text().equals(nextPageButton))
                    .map(link -> link.attr("abs:href")).findAny().orElse(null);

            Elements titles = document.getElementsByClass("nameRus");
            resultSet.addAll(
                    titles.stream().map(title -> title.getElementsByTag("a").first())
                            .map(link -> new Title(link.text(),
                                    link.attr("abs:href")))
                            .collect(Collectors.toSet())
            );
        }
        return resultSet;
    }

    private static Set<Title> parseImdb(String url) throws IOException {
        Set<Title> resultSet = new HashSet<>();
        Document document = null;
        Element nextButton = null;
        do {
            document = Jsoup.connect(url).maxBodySize(0).get();
            nextButton = document.getElementsByClass("next-page").first();
            if (nextButton != null)
                url = nextButton.attr("abs:href");

            Elements titles = document.getElementsByClass("lister-item-header");
            resultSet.addAll(
                    titles.stream().map(title -> title.getElementsByTag("a").first())
                            .map(link -> new Title(link.text(),
                                    link.attr("abs:href").split("\\?")[0]))
                            .collect(Collectors.toSet())
            );
        } while (nextButton != null && !nextButton.hasClass("disabled"));
        return resultSet;
    }

    private static Set<Title> parseMyanimelist(String url) throws IOException {
        final String baseUrl = "https://myanimelist.net";
        Document document = Jsoup.connect(url).maxBodySize(0).get();
        String jsonTitles = document.getElementsByTag("table")
                .attr("data-items");

        ObjectMapper objectMapper = new ObjectMapper();
        HashSet<Title> resultSet = objectMapper.readValue(jsonTitles,
                new TypeReference<HashSet<Title>>() {
                });
        for (Title title : resultSet)
            title.setLink(baseUrl + title.getLink());
        return resultSet;
    }

    private static Set<Title> parseLetterboxd(String url) throws IOException {
        Set<Title> resultSet = new HashSet<>();
        Document document = null;
        while (!url.equals("")) {
            document = Jsoup.connect(url).maxBodySize(0).get();
            url = document.getElementsByClass("next").first().attr("abs:href");
            Elements titles = document.getElementsByClass("linked-film-poster");
            //we can obtain a title only from image's alt attribute, which buried into a div tag
            resultSet.addAll(
                    titles.stream()
                            .map(link -> {
                                Title tempTitle = new Title();
                                tempTitle.setLink(link.attr("abs:data-target-link"));
                                link = link.getElementsByTag("img").first();
                                tempTitle.setTitle(link.attr("alt"));
                                return tempTitle;
                            })
                            .collect(Collectors.toSet())
            );
        }
        return resultSet;
    }

    public static Set<Title> load(String filename) throws IOException {
        return load(Path.of(filename));
    }

    public static Set<Title> load(Path filename) throws IOException {
        return load(Files.newInputStream(filename));
    }

    public static Set<Title> load(InputStream titles) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(titles,
                new TypeReference<HashSet<Title>>() {
                });
    }


    public static void save(Set<Title> titles) throws IOException {
        save(titles, "my_titles_" + FORMATTER.format(new Date()) + ".json");
    }

    public static void save(Set<Title> titles, String filename) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(Files.newOutputStream(Path.of(filename)), titles);
    }

    @JsonIgnoreProperties(value = {"status", "score", "tags", "is_rewatching", "num_watched_episodes",
            "created_at", "updated_at", "anime_title_eng", "anime_num_episodes", "anime_airing_status", "anime_id",
            "anime_studios", "anime_licensors", "anime_season", "anime_total_members", "anime_total_scores",
            "anime_score_val", "has_episode_video", "has_promotion_video", "has_video", "genres", "demographics",
            "title_localized", "video_url", "anime_image_path", "is_added_to_list", "anime_media_type_string",
            "anime_mpaa_rating_string", "start_date_string", "finish_date_string", "anime_start_date_string",
            "anime_end_date_string", "days_string", "storage_string", "priority_string", "notes",
            "editable_notes", "id", "is_rereading",  "num_read_chapters",  "num_read_volumes", "manga_english",
            "manga_num_chapters", "manga_num_volumes", "manga_publishing_status", "manga_id", "manga_magazines",
            "manga_total_members", "manga_total_scores", "manga_score_val",  "manga_image_path",
            "manga_media_type_string",  "manga_end_date_string", "retail_string", "manga_start_date_string"
    })
    public static class Title {
        @JsonProperty("title")
        @JsonAlias({"anime_title", "manga_title"})
        private String title;
        @JsonProperty("link")
        @JsonAlias({"anime_url", "manga_url"})
        private String link;

        public Title() {
        }

        public Title(String title, String link) {
            this.title = title;
            this.link = link;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Title title = (Title) o;
            return Objects.equals(link, title.link);
        }

        @Override
        public int hashCode() {
            return Objects.hash(link);
        }

        @Override
        public String toString() {
            return "Title{" +
                    "title='" + title + '\'' +
                    ", link='" + link + '\'' +
                    '}';
        }
    }
}