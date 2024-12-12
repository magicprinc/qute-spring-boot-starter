package net.snemeis.quteproduction.controller;

import com.thedeanda.lorem.LoremIpsum;
import io.quarkus.qute.Qute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.sql.Struct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Controller
public class HomeController {

    @GetMapping("/")
    @ResponseBody
    String index(Model model) {
        // dev mode
        // ============================
        Qute.engine().clearTemplates();
        // ----------------------------

        // dummy data
        var lorem = LoremIpsum.getInstance();
        List<String> filters = List.of("filter.availability", "filter.product_type", "filter.approbation");
        List<Product> products = new ArrayList<>();
        IntStream.range(0, 10)
                 .forEach((num) -> products.add(new Product(lorem.getWords(2, 5), lorem.getZipCode(), lorem.getWords(15, 25))));

        // get template
        var template = Qute.engine().getTemplate("vindex");

        // set model data and render template
        return template
                .data("filters", filters)
                .data("hits", 10009)
                .data("products", products)
                .render();
    }

    record Product(String name, String productId, String description) {}
}
