package net.snemeis;

import io.quarkus.qute.TemplateException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "spring.qute")
public class QuteProperties {

    /*
     * General Configs
     */
    public List<String> suffixes = List.of("qute.html", "qute.txt", "html,txt");

    public Map<String, String> contentTypes = Map.of();

    public List<String> typeCheckExcludes = List.of();

    public Pattern templatePathExclude = Pattern.compile("^\\..*|.*\\/\\..*$");

    public String iterationMetadataPrefix = "<alias_>";

    public List<String> escapeContentTypes = List.of("text/html", "text/xml", "application/xml", "application/xhtml+xml");

    public Charset defaultCharset = StandardCharsets.UTF_8;

    /*
     * Temporary Extra Configs
     */
    Set<String> templateRoots = Set.of("/"); // TODO: I don't know if this is correct
    List<String> templatePaths = List.of("templates/");
    List<String> resolverClasses = List.of(); // TODO: is this relevant?
    Locale defaultLocale = Locale.ENGLISH; // TODO: is this relevant?

    /*
     * Runtime Configs
     */
    public PropertyNotFoundStrategy propertyNotFoundStrategy = PropertyNotFoundStrategy.DEFAULT;

    public boolean removeStandaloneLines = true;

    public boolean strictRendering = true;

    public long timeout = 10000;

    public boolean useAsyncTimeout = true;

    public enum PropertyNotFoundStrategy {
        /**
         * Output the {@code NOT_FOUND} constant.
         */
        DEFAULT,
        /**
         * No operation - no output.
         */
        NOOP,

        /**
         * Throw a {@link TemplateException}.
         */
        THROW_EXCEPTION,
        /**
         * Output the original expression string, e.g. <code>{foo.name}</code>.
         */
        OUTPUT_ORIGINAL
    }
}
