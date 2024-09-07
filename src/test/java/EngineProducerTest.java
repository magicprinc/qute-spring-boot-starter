import io.quarkus.qute.Qute;
import net.snemeis.EngineProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = { EngineProducer.class, EngineProducerTest.SampleComponent.class })
public class EngineProducerTest {

    @Autowired
    ApplicationContext context;

    @Test
    void Qute_engine_is_being_set() {
        assertNotNull(Qute.engine());
    }

    @Test
    void qute_reners_some_string() {
        String out = Qute.engine().parse("hello from qute :)").render();
        assertEquals("hello from qute :)", out);
    }

    @Test
    void sample_component_is_resolvable() {
        SampleComponent sampleComponent = (SampleComponent) context.getBean("SampleComponent");

        String numResult = sampleComponent.getNumberAsString(42);
        String i18Result = sampleComponent.translate("test.one");

        assertEquals("{42}", numResult);
        assertEquals("default message", i18Result);
    }

    @Test
    void qute_resolves_bean_and_applies_its_method() {
        String out = Qute.engine().parse("{inject:SampleComponent.getNumberAsString(42)}").render();
        assertEquals("{42}", out);
    }

    @Test
    void qute_resolves_bean_and_applies_its_method_with_data() {
        String out = Qute.engine().parse("{cdi:SampleComponent.getNumberAsString(num)}").data("num", 42).render();
        assertEquals("{42}", out);
    }

    @Test
    void qute_translates_messages_via_the_sample_component() {
        String out = Qute.engine().parse("This is the translated message: {cdi:SampleComponent.translate('something')}").render();
        assertEquals("This is the translated message: default message", out);
    }

    @TestComponent(value = "SampleComponent")
    public static class SampleComponent {

        @Autowired
        MessageSource messageSource;

        public String getNumberAsString(int number) {
            return "{%d}".formatted(number);
        }

        public String translate(String key, String... args) {
            return messageSource.getMessage(key, args, "default message", Locale.ENGLISH);
        }
    }
}
