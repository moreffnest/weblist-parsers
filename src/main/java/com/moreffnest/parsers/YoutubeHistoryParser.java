package com.moreffnest.parsers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moreffnest.parsers.exceptions.InvalidFileExtensionException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class YoutubeHistoryParser {

    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss");
    public enum FileType {JSON, HTML}

    public static Set<YoutubeVideo> parse(Path filepath) throws IOException, InvalidFileExtensionException {
        return parse(filepath.toString());
    }
    public static Set<YoutubeVideo> parse(String filename) throws IOException, InvalidFileExtensionException {
        try {
            return parse(Files.newInputStream(Path.of(filename)),
                    FileType.valueOf(com.google.common.io.Files.getFileExtension(filename).toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new InvalidFileExtensionException("The file must have .html or .json extension!");
        }
    }

    public static Set<YoutubeVideo> parse(InputStream inputStream, FileType extension) throws IOException, InvalidFileExtensionException {
        return switch (extension) {
            case HTML -> parseHtml(inputStream);
            case JSON -> parseJson(inputStream);
        };
    }


    private static Set<YoutubeVideo> parseHtml(InputStream ytHistory) throws IOException {
        Set<YoutubeVideo> videos = new HashSet<>();

        Document document = Jsoup.parse(ytHistory, "UTF-8", "");
        Elements videosHtml = document.getElementsByClass(
                "content-cell mdl-cell mdl-cell--6-col mdl-typography--body-1");
        for (Element video : videosHtml) {
            Elements videoLinks = video.getElementsByTag("a");
            //some of yt videos in history could be deleted
            if (videoLinks.size() < 2)
                continue;
            Element videoLink = videoLinks.get(0);
            Element channelLink = videoLinks.get(1);
            videos.add(new YoutubeVideo(
                            videoLink.text(),
                            channelLink.text(),
                            videoLink.attr("href")
                    )
            );
        }
        return videos;
    }

    private static Set<YoutubeVideo> parseJson(InputStream ytHistory) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(ytHistory,
                new TypeReference<HashSet<YoutubeVideo>>() {
                });
    }

    public static void save(Set<YoutubeVideo> videos) throws IOException {
        save(videos, "yt_videos_" + FORMATTER.format(new Date()) + ".json");
    }

    public static void save(Set<YoutubeVideo> videos, String filename) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(Files.newOutputStream(Path.of(filename)), videos);
    }



    public static class YoutubeVideo {
        private String title;
        private String channelName;
        private String link;

        public YoutubeVideo() {
        }

        public YoutubeVideo(String title, String channelName, String link) {
            this.title = title;
            this.channelName = channelName;
            this.link = link;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannelName(String channelName) {
            this.channelName = channelName;
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
            YoutubeVideo that = (YoutubeVideo) o;
            return Objects.equals(link, that.link);
        }

        @Override
        public int hashCode() {
            return Objects.hash(link);
        }
    }
}