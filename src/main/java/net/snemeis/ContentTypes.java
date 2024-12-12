package net.snemeis;

import io.quarkus.qute.Variant;
//import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLConnection;

//@Slf4j
@Component
public class ContentTypes {

    @Autowired
    QuteProperties config;

    /**
     *
     * @param templatePath The path relative to the template root, uses the {@code /} path separator.
     * @return the content type
     */
    public String getContentType(String templatePath) {
        String fileName = templatePath;
        int slashIdx = fileName.lastIndexOf('/');
        if (slashIdx != -1) {
            fileName = fileName.substring(slashIdx);
        }
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx != -1) {
            String suffix = fileName.substring(dotIdx + 1);
            String additionalContentType = config.contentTypes.get(suffix);
            if (additionalContentType != null) {
                return additionalContentType;
            }
            if (suffix.equalsIgnoreCase("json")) {
                return Variant.APPLICATION_JSON;
            }
            String contentType = URLConnection.getFileNameMap().getContentTypeFor(fileName);
            if (contentType != null) {
                return contentType;
            }
        }
//        log.warn("Unable to detect the content type for {}; using application/octet-stream", templatePath);
        System.out.println("unable to detect content type");
        return "application/octet-stream";
    }
}
