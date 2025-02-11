package net.snemeis.quteproduction.configs;

import io.quarkus.qute.Qute;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.lang.System.Logger.Level.INFO;

@Configuration
public class QuteTemplateEngine {

    @Autowired
    MessageSource messageSource;

    @Autowired
    private View view;

    private static final System.Logger logger = System.getLogger(QuteTemplateEngine.class.getName());

    @Bean
    ViewResolver quteViewResolver() {
        return (viewName, locale) -> new View() {

            @Override
            public String getContentType() {
                return "text/html";
            }

            @Override
            public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
                logger.log(INFO, "rendering template output");
                var template = Qute.engine()
                        .getTemplate(viewName);

                // add model data to template 1:1
//                model.forEach((key, value) -> {
//                    var tmpNew = template.data(key, value);
//                    template = tmpNew;
//                });

                var html = template.render(Map.of("name", "World"));

                response.setContentType(MediaType.TEXT_HTML_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(html);
            }
        };
    }
}
