package me.geek.tom.mcbot.mappings;

import com.google.common.net.UrlEscapers;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

public class MavenDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenDownloader.class);
    private static final String mavenUrlTemplate = "%1$s/%2$s/%3$s/%2$s-%3$s";

    private final File dataDirectory;
    private final String repoUrl;

    public MavenDownloader(File dataDirectory, String repoUrl) {
        this.dataDirectory = dataDirectory;
        this.repoUrl = repoUrl;
    }

    public Mono<File> download(String group, String name, String version, @Nullable String classifier, String extension) {
        File filename = getFileName(group, name, version, classifier, extension);
        File md5 = getFileName(group, name, version, classifier, extension + ".md5");
        String mavenUrl = createMavenUrlString(group, name, version, classifier, extension);
        return isUpToDate(filename, md5, mavenUrl + ".md5")
                .flatMap(upToDate -> {
                    if (upToDate) {
                        LOGGER.debug(toMavenString(group, name, version, classifier) + " is up to date!");
                        return Mono.just(filename);
                    }
                    return Mono.create(sink -> {
                        try {
                            String mvnStr = toMavenString(group, name, version, classifier);
                            LOGGER.debug("Downloading: " + mvnStr);
                            FileUtils.copyURLToFile(new URL(mavenUrl), filename);
                            LOGGER.debug("Downloading MD5 hash for " + mvnStr);
                            FileUtils.copyURLToFile(new URL(mavenUrl + ".md5"), md5);
                            sink.success(filename);
                        } catch (IOException e) {
                            sink.error(e);
                        }
                    });
                });
    }

    private Mono<Boolean> isUpToDate(File f, File md5, String md5Url) {
        LOGGER.debug("Exists: " + (md5.exists() || f.exists()));
        if (!md5.exists() || !f.exists()) return Mono.just(false);

        return Mono.create(sink -> {
            try {
                String currentMd5 = FileUtils.readFileToString(md5, Charset.defaultCharset());
                String serverMd5 = IOUtils.toString(new URL(md5Url), Charset.defaultCharset());
                sink.success(Objects.equals(currentMd5, serverMd5));
            } catch (IOException e) {
                sink.error(e);
            }
        });
    }

    private String toMavenString(String group, String name, String version, @Nullable String classifier) {
        return group + ":" + name + ":" + version + (classifier != null && !classifier.isEmpty() ? ":" + classifier : "");
    }

    private File getFileName(String group, String name, String version, String classifier, String extension) {
        Path path = dataDirectory.toPath();
        for (String part : group.split("\\."))
            path = path.resolve(part);
        String filename = name + "-" + version;
        if (classifier != null && !classifier.isEmpty())
            filename += "-" + classifier;
        filename += "." + extension;

        return path.resolve(name)
                .resolve(version)
                .resolve(filename).toFile();
    }

    private String createMavenUrlString(String group, String name, String version, @Nullable String classifier, String extension) {
        StringBuilder builder = new StringBuilder();
        if (!repoUrl.endsWith("/"))
            builder.append("/");
        builder.append(String.format(mavenUrlTemplate,
                UrlEscapers.urlPathSegmentEscaper().escape(group).replace(".", "/"),
                UrlEscapers.urlPathSegmentEscaper().escape(name),
                UrlEscapers.urlPathSegmentEscaper().escape(version)));
        if (classifier != null && !classifier.isEmpty())
            builder.append("-").append(UrlEscapers.urlPathSegmentEscaper().escape(classifier));
        builder.append(".").append(UrlEscapers.urlPathSegmentEscaper().escape(extension));
        return repoUrl + builder.toString();
    }
}
