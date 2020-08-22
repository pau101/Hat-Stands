package me.paulf.hatstands.server;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.gson.stream.JsonReader;
import com.mojang.authlib.HttpAuthenticationService;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

public final class News {
    private News() {}

    private static final URL CONTENT = HttpAuthenticationService.constantURL(
        "https://www.minecraft.net/content/minecraft-net/_jcr_content.articles.grid?" +
        "tileselection=auto&" +
        "tagsPath=minecraft:article/culture,minecraft:article/insider,minecraft:article/merch,minecraft:article/news,minecraft:stockholm/news,minecraft:stockholm/merch,minecraft:stockholm/minecraft&" +
        "propResPath=/content/minecraft-net/language-masters/en-us/jcr:content/root/generic-container/par/grid&" +
        "count=500&" +
        "pageSize=6&" +
        "lang=/content/minecraft-net/language-masters/en-us"
    );

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
        .appendLiteral(' ')
        .appendText(MONTH_OF_YEAR)
        .appendLiteral(' ')
        .appendValue(YEAR, 4)
        .appendLiteral(' ')
        .appendValue(HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(MINUTE_OF_HOUR, 2)
        .optionalStart()
        .appendLiteral(':')
        .appendValue(SECOND_OF_MINUTE, 2)
        .optionalEnd()
        .appendLiteral(' ')
        .appendZoneId()
        .toFormatter(Locale.ROOT);

    public static List<Article> get(final Proxy proxy) throws IOException {
        URL url = CONTENT;
        HttpURLConnection conn;
        int redirect = 0;
        int response;
        do {
            conn = (HttpURLConnection) url.openConnection(proxy);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Encoding", "gzip,deflate");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; rv:52.0) Gecko/20100101 Firefox/52.0");
            conn.connect();
            response = conn.getResponseCode();
            if (response >= 300 && response <= 399) {
                final String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    if (loc.startsWith("/")) {
                        url = new URL(url, loc);
                    } else if (!url.getProtocol().equals((url = new URL(loc)).getProtocol())) {
                        throw new IOException("bad protocol");
                    }
                }
            } else {
                break;
            }
        } while (redirect++ < 5);
        if (response != HttpURLConnection.HTTP_OK) {
            throw new IOException(String.format("bad response: %d %s", response, conn.getResponseMessage()));
        }
        final String contentType = conn.getContentType();
        if (contentType == null) {
            throw new IOException("bad content type");
        }
        final int p = contentType.indexOf(';');
        final String mimetype = p > 0 ? contentType.substring(0, p) : contentType;
        if (!"application/json".equals(mimetype)) {
            throw new IOException("bad mimetype: " + mimetype);
        }
        final String CHARSET_KEY = "charset=";
        final int c = contentType.indexOf(CHARSET_KEY, p + 1);
        final Charset charset;
        if (c > 0) {
            final int charsetStart = c + CHARSET_KEY.length();
            final int charsetEnd = contentType.indexOf(';', charsetStart);
            try {
                charset = Charset.forName(contentType.substring(charsetStart, charsetEnd > charsetStart ? charsetEnd : contentType.length()));
            } catch (final IllegalArgumentException e) {
                throw new IOException("bad charset", e);
            }
        } else {
            charset = StandardCharsets.UTF_8;
        }
        final String encoding = conn.getContentEncoding();
        try (final InputStream identity = ByteStreams.limit(conn.getInputStream(), 65536)) {
            final InputStream in;
            if ("gzip".equalsIgnoreCase(encoding)) {
                in = new GZIPInputStream(identity);
            } else if ("deflate".equalsIgnoreCase(encoding)) {
                in = new InflaterInputStream(identity);
            } else {
                in = identity;
            }
            try (final JsonReader json = new JsonReader(new InputStreamReader(in, charset))) {
                return read(url, json);
            }
        }
    }

    private static List<Article> read(final URL base, final JsonReader json) throws IOException {
        final List<Article> articles = new ArrayList<>();
        json.beginObject();
        while (json.hasNext()) {
            if ("article_grid".equals(json.nextName())) {
                json.beginArray();
                while (json.hasNext()) {
                    final Article article = new Article();
                    json.beginObject();
                    while (json.hasNext()) {
                        switch (json.nextName()) {
                            case "default_tile":
                                json.beginObject();
                                while (json.hasNext()) {
                                    switch (json.nextName()) {
                                        case "title":
                                            article.title = json.nextString();
                                            break;
                                        case "sub_header":
                                            article.subHeader = json.nextString();
                                            break;
                                        default:
                                            json.skipValue();
                                    }
                                }
                                json.endObject();
                                break;
                            case "article_url":
                                try {
                                    article.url = new URL(base, json.nextString());
                                } catch (final MalformedURLException e) {
                                    throw new IOException("malformed url", e);
                                }
                                break;
                            case "publish_date":
                                try {
                                    article.date = LocalDate.parse(json.nextString(), FORMATTER);
                                } catch (final DateTimeParseException e) {
                                    throw new IOException("malformed date", e);
                                }
                                break;
                            default:
                                json.skipValue();
                        }
                    }
                    json.endObject();
                    if (Strings.isNullOrEmpty(article.title) ||
                        Strings.isNullOrEmpty(article.subHeader) ||
                        article.url == null) {
                        throw new IOException("malformed article");
                    } else {
                        articles.add(article);
                    }
                }
                json.endArray();
            } else {
                json.skipValue();
            }
        }
        json.endObject();
        return articles;
    }

    public static final class Article {
        private String title;

        private String subHeader;

        private URL url;

        private LocalDate date;

        private Article() {
        }

        public String getTitle() {
            return this.title;
        }

        public String getSubHeader() {
            return this.subHeader;
        }

        public URL getUrl() {
            return this.url;
        }

        public LocalDate getDate() {
            return this.date;
        }
    }
}
