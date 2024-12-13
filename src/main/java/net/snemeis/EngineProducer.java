package net.snemeis;

import io.quarkus.qute.*;
import io.quarkus.qute.TemplateInstance.Initializer;
import io.quarkus.qute.TemplateLocator.TemplateLocation;
import jdk.jshell.spi.ExecutionControl.NotImplementedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Slf4j
@AutoConfiguration(after = { WebMvcAutoConfiguration.class, WebFluxAutoConfiguration.class })
@EnableConfigurationProperties(QuteProperties.class)
@Import(ContentTypes.class)
public class EngineProducer {

    public static final String INJECT_NAMESPACE = "inject";
    public static final String CDI_NAMESPACE = "cdi";
    public static final String DEPENDENT_INSTANCES = "q_dep_inst";

    private static final String TAGS = "tags/";

    private final Engine engine;
    private final ContentTypes contentTypes;
    private final List<String> tags;
    private final List<String> suffixes;
    private final Set<String> templateRoots;
    private final Map<String, String> templateContents;
    private final Pattern templatePathExclude;
    private final Locale defaultLocale;
    private final Charset defaultCharset;
    private final Boolean devMode;
    private final String devPrefix;
    private final ApplicationContext container; // TODO: rename context, as spring would do it
    private final Environment environment;

    public EngineProducer(
//            QuteRecorder.QuteContext context, // not needed?
            QuteProperties config,
            ContentTypes contentTypes,
//            LocalesBuildTimeConfig locales,
            List<TemplateLocator> locators,
            List<SectionHelperFactory<?>> sectionHelperFactories,
            List<ValueResolver> valueResolvers,
            List<NamespaceResolver> namespaceResolvers,
            List<ParserHook> parserHooks,
            ApplicationContext context,
            Environment environment,
            @Value("${spring.qute.dev-mode:false}") boolean devMode,
            @Value("${spring.qute.dev-prefix:}") String devPrefix
    ) {
        /* TODO: check what needs to be done here
            this.templateRoots = context.getTemplateRoots();                    // probably can be put into the QuteProperties
            this.defaultLocale = locales.defaultLocale;                         // can be put into QuteProperties
            this.templateContents = Map.copyOf(context.getTemplateContents());  // needs to be read out when the application starts
            this.tags = context.getTags();                                      // needs to be read out when the application starts
         */
        this.templateRoots = config.templateRoots;
        this.defaultLocale = config.defaultLocale;
//        this.templateContents = Map.of("index", "this is the index content????", "testTagOne", "this is the first test tag");
        this.templateContents = new HashMap<>();
//        this.tags = List.of("testTagOne");
        this.tags = List.of();

        this.contentTypes = contentTypes;
        this.suffixes = config.suffixes;
        this.templatePathExclude = config.templatePathExclude;
        this.defaultCharset = config.defaultCharset;

        // TODO: test that    devmode resolves files by filepath
        // TODO: test that no devmode resolves files by classpath
        this.devMode = devMode;
        this.devPrefix = devPrefix;

//        this.container = Arc.container();
        this.container = context;
        this.environment = environment;

//        log.debug("Initializing Qute [templates: {}, tags: {}, resolvers: {}", context.getTemplatePaths(), tags,
//                context.getResolverClasses());
//        log.debug("Initializing Qute [templates: {}, tags: {}, resolvers: {}", config.templatePaths, tags,
//                config.resolverClasses);
        System.out.println("initializing something in qute starter");

        EngineBuilder builder = Engine.builder();

        // We don't register the map resolver because of param declaration validation
        builder.addValueResolver(ValueResolvers.thisResolver());
        builder.addValueResolver(ValueResolvers.orResolver());
        builder.addValueResolver(ValueResolvers.trueResolver());
        builder.addValueResolver(ValueResolvers.collectionResolver());
        builder.addValueResolver(ValueResolvers.mapperResolver());
        builder.addValueResolver(ValueResolvers.mapEntryResolver());
        // foo.string.raw returns a RawString which is never escaped
        builder.addValueResolver(ValueResolvers.rawResolver()); // TODO: look into this for learning
        builder.addValueResolver(ValueResolvers.logicalAndResolver());
        builder.addValueResolver(ValueResolvers.logicalOrResolver());
        builder.addValueResolver(ValueResolvers.orEmpty());
        // Note that arrays are handled specifically during validation
        builder.addValueResolver(ValueResolvers.arrayResolver());
        // Additional value resolvers
        for (ValueResolver valueResolver : valueResolvers) {
            builder.addValueResolver(valueResolver);
        }

        // Enable/disable strict rendering
        if (config.strictRendering) {
            builder.strictRendering(true);
        } else {
            builder.strictRendering(false);

            // Not needed
//            // If needed, use a specific result mapper for the selected strategy
//            switch (config.propertyNotFoundStrategy) {
//                case THROW_EXCEPTION:
//                    builder.addResultMapper(new PropertyNotFoundThrowException());
//                    break;
//                case NOOP:
//                    builder.addResultMapper(new PropertyNotFoundNoop());
//                    break;
//                case OUTPUT_ORIGINAL:
//                    builder.addResultMapper(new PropertyNotFoundOutputOriginal());
//                    break;
//                default:
//                    // Use the default strategy
//                    break;
//            }

            // Throw an exception in the development mode regardless the propertyNotFoundStrategy
            if (environment.acceptsProfiles(Profiles.of("dev"))) {
                builder.addResultMapper(new PropertyNotFoundThrowException());
            }
        }

        // Escape some characters for HTML/XML templates
        builder.addResultMapper(new HtmlEscaper(List.copyOf(config.escapeContentTypes)));

        // Fallback reflection resolver
        builder.addValueResolver(new ReflectionValueResolver());

        // Remove standalone lines if desired
        builder.removeStandaloneLines(config.removeStandaloneLines);

        // Iteration metadata prefix
        builder.iterationMetadataPrefix(config.iterationMetadataPrefix);

        // Default section helpers
        builder.addDefaultSectionHelpers();

        // Additional section helpers
        for (SectionHelperFactory<?> sectionHelperFactory : sectionHelperFactories) {
            builder.addSectionHelper(sectionHelperFactory);
        }

        // Allow anyone to customize the builder
        // collect custom builder customizations before actually building the engine
        // TODO: how to do this in spring?
//        builderReady.fire(builder);

        // Resolve @Named beans
        builder.addNamespaceResolver(NamespaceResolver.builder(INJECT_NAMESPACE).resolve(this::resolveInject).build());
        builder.addNamespaceResolver(NamespaceResolver.builder(CDI_NAMESPACE).resolve(this::resolveInject).build());
        // Additional namespace resolvers
        for (NamespaceResolver namespaceResolver : namespaceResolvers) {
            builder.addNamespaceResolver(namespaceResolver);
        }

        // Add generated resolvers
        for (String resolverClass : config.resolverClasses) {
            Resolver resolver = createResolver(resolverClass);
            if (resolver instanceof NamespaceResolver) {
                builder.addNamespaceResolver((NamespaceResolver) resolver);
            } else {
                builder.addValueResolver((ValueResolver) resolver);
            }
//            log.debug("Added generated value resolver: {}", resolverClass);
            System.out.println("added some value resolver");
        }

        // Add tags
        for (String tag : tags) {
            // Strip suffix, item.html -> item
            String tagName = tag.contains(".") ? tag.substring(0, tag.indexOf('.')) : tag;
            String tagTemplateId = TAGS + tagName;
//            log.debug("Registered UserTagSectionHelper for {} [{}]", tagName, tagTemplateId);
            System.out.println("did something something usertagselectionhelper");
            builder.addSectionHelper(new UserTagSectionHelper.Factory(tagName, tagTemplateId));
        }

        // Add locator
        builder.addLocator(this::locate);
        registerCustomLocators(builder, locators);

        // Add parser hooks
        for (ParserHook parserHook : parserHooks) {
            builder.addParserHook(parserHook);
        }
        // Add a special parser hook for Qute.fmt() methods
        builder.addParserHook(new Qute.IndexedArgumentsParserHook());

        // Add global providers
//        TODO: do this
//        for (String globalProviderClass : context.getTemplateGlobalProviderClasses()) {
//            TemplateGlobalProvider provider = createGlobalProvider(globalProviderClass);
//            builder.addTemplateInstanceInitializer(provider);
//            builder.addNamespaceResolver(provider);
//        }

        // Add a special initializer for templates that contain an inject/cdi namespace expressions
        Map<String, Boolean> discoveredInjectTemplates = new HashMap<>();
        builder.addTemplateInstanceInitializer(new Initializer() {

            @Override
            public void accept(TemplateInstance instance) {
                Boolean hasInject = discoveredInjectTemplates.get(instance.getTemplate().getGeneratedId());
                if (hasInject == null) {
                    hasInject = hasInjectExpression(instance.getTemplate());
                }
                if (hasInject) {
                    // Add dependent beans map if the template contains a cdi namespace expression
                    instance.setAttribute(DEPENDENT_INSTANCES, new ConcurrentHashMap<>());

                    /* TODO: Not relevant for spring??  */
                    // Add a close action to destroy all dependent beans
//                    instance.onRendered(new Runnable() {
//                        @Override
//                        public void run() {
//                            Object dependentInstances = instance.getAttribute(EngineProducer.DEPENDENT_INSTANCES);
//                            if (dependentInstances != null) {
//                                @SuppressWarnings("unchecked")
//                                ConcurrentMap<String, InstanceHandle<?>> existing = (ConcurrentMap<String, InstanceHandle<?>>) dependentInstances;
//                                if (!existing.isEmpty()) {
//                                    for (InstanceHandle<?> handle : existing.values()) {
//                                        handle.close();
//                                    }
//                                }
//                            }
//                        }
//                    });
                }
            }
        });

        builder.timeout(config.timeout);
        builder.useAsyncTimeout(config.useAsyncTimeout);

        engine = builder.build();

        // Load discovered template files
        // pre-fill some list with some pre-defined templates (optionally)
        Map<String, List<Template>> discovered = new HashMap<>();
        for (String path : config.templatePaths) {
            Template template = engine.getTemplate(path);
            if (template != null) {
                for (String suffix : config.suffixes) {
                    if (path.endsWith(suffix)) {
                        String pathNoSuffix = path.substring(0, path.length() - (suffix.length() + 1));
                        List<Template> templates = discovered.get(pathNoSuffix);
                        if (templates == null) {
                            templates = new ArrayList<>();
                            discovered.put(pathNoSuffix, templates);
                        }
                        templates.add(template);
                        break;
                    }
                }
                discoveredInjectTemplates.put(template.getGeneratedId(), hasInjectExpression(template));
            }
        }
        // If it's a default suffix then register a path without suffix as well
        // hello.html -> hello, hello.html
        for (Map.Entry<String, List<Template>> e : discovered.entrySet()) {
            processDefaultTemplate(e.getKey(), e.getValue(), config, engine);
        }

        // TODO: is this needed???
//        engineReady.fire(engine);

        // Set the engine instance
        // TODO: check what you can do with this, seems funny
        Qute.setEngine(engine);
    }

    private void registerCustomLocators(EngineBuilder builder,
                                        List<TemplateLocator> locators) {
        if (locators != null && !locators.isEmpty()) {
            for (TemplateLocator locator : locators) {
                builder.addLocator(locator);
            }
        }
    }

//    @Produces
//    @ApplicationScoped
    @Bean
    Engine getEngine() {
        return engine;
    }

    // Not needed for spring??
    void onShutdown() throws NotImplementedException {
        throw new NotImplementedException("no shutdown handler is implemented");
    }

    private Resolver createResolver(String resolverClassName) {
        try {
            Class<?> resolverClazz = getClass().getClassLoader().loadClass(resolverClassName);
            if (Resolver.class.isAssignableFrom(resolverClazz)) {
                return (Resolver) resolverClazz.getDeclaredConstructor().newInstance();
            }
            throw new IllegalStateException("Not a resolver: " + resolverClassName);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | IllegalArgumentException
                 | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            throw new IllegalStateException("Unable to create resolver: " + resolverClassName, e);
        }
    }

    // TODO: fix this, as global providers basically provide global variables
//    private TemplateGlobalProvider createGlobalProvider(String initializerClassName) {
//        try {
//            Class<?> initializerClazz = Thread.currentThread()
//                    .getContextClassLoader().loadClass(initializerClassName);
//            if (TemplateGlobalProvider.class.isAssignableFrom(initializerClazz)) {
//                return (TemplateGlobalProvider) initializerClazz.getDeclaredConstructor().newInstance();
//            }
//            throw new IllegalStateException("Not a global provider: " + initializerClazz);
//        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | IllegalArgumentException
//                 | InvocationTargetException | NoSuchMethodException | SecurityException e) {
//            throw new IllegalStateException("Unable to create global provider: " + initializerClassName, e);
//        }
//    }

    private Optional<TemplateLocation> locate(String path) {
        if (templatePathExclude.matcher(path).matches()) {
            return Optional.empty();
        }
        // TODO: if dev mode, try to search for it not in the classpath, but in the actual folder it resides in
        // so: ${user.dir}/...
        if (this.devMode) {
//            engine.clearTemplates();
            String templatePath = this.devPrefix + path;
            URL resource =  null;
            System.out.println("Dev path would be: " + templatePath);

            for (String suffix : suffixes) {
                String pathWithSuffix = templatePath + "." + suffix;
                if (templatePathExclude.matcher(pathWithSuffix).matches()) {
                    continue;
                }

                File file = new File(pathWithSuffix);
                if (!file.exists() && file.isDirectory()) {
                    continue;
                }

                try {
                    templateContents.put(path, FileUtils.readFileToString(file, StandardCharsets.UTF_8));
                    break;
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }

        // TODO: fill this via config parameter
        List<String> classPathPrefixes = List.of("lib1", "lib2");
        // TODO: properly calculate and actullay use
        boolean isExclusivelyInClassPathForExampleFragments = false;

        // locate by classpath
        if(!devMode || (devMode && isExclusivelyInClassPathForExampleFragments)) {
            // First try to locate file-based templates
            for (String templateRoot : templateRoots) {
                URL resource = null;
                String templatePath = templateRoot + path;
    //            log.debug("Locate template file for {}", templatePath);
                System.out.println(templatePath);
    //            resource = locatePath(templatePath);
                resource = locatePath(templatePath);
                if (resource == null) {
                    // Try path with suffixes
                    for (String suffix : suffixes) {
                        String pathWithSuffix = path + "." + suffix;
                        if (templatePathExclude.matcher(pathWithSuffix).matches()) {
                            continue;
                        }
                        templatePath = templateRoot + pathWithSuffix;
                        resource = locatePath(templatePath);
                        if (resource != null) {
                            break;
                        }
                    }
                }
                if (resource != null) {
                    return Optional.of(new ResourceTemplateLocation(resource, createVariant(templatePath)));
                }
            }
        }

//        log.debug("Locate template contents for {}", path);
        String content = templateContents.get(path);
        if (path == null) {
            // Try path with suffixes
            for (String suffix : suffixes) {
                String pathWithSuffix = path + "." + suffix;
                if (templatePathExclude.matcher(pathWithSuffix).matches()) {
                    continue;
                }
                content = templateContents.get(pathWithSuffix);
                if (content != null) {
                    break;
                }
            }
        }
        if (content != null) {
            return Optional.of(new ContentTemplateLocation(content, createVariant(path)));
        }
        return Optional.empty();
    }

    private URL locatePath(String path) {
        try {
            var resource = Objects.requireNonNull(getClass().getResource(path));
            return resource.toURI().toURL();
        } catch (Exception e) {
            return null;
        }
    }

    Variant createVariant(String path) {
        // Guess the content type from the path
        String contentType = contentTypes.getContentType(path);
        return new Variant(defaultLocale, defaultCharset, contentType);
    }

    private Object resolveInject(EvalContext ctx) {
        if (container.containsBean(ctx.getName())) {
            Object bean = container.getBean(ctx.getName());

            Object dependentInstances = ctx.getAttribute(EngineProducer.DEPENDENT_INSTANCES);

            // TODO: is this block needed?
            if (dependentInstances != null) {
                @SuppressWarnings("unchecked")
                ConcurrentMap<String, Object> existing = (ConcurrentMap<String, Object>) dependentInstances;
                return existing.computeIfAbsent(ctx.getName(), name -> bean);
            }

            return bean;
        }

        return Results.NotFound.from(ctx);
    }

    private boolean hasInjectExpression(Template template) {
        for (Expression expression : template.getExpressions()) {
            if (isInjectExpression(expression)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInjectExpression(Expression expression) {
        // check the namespace
        String namespace = expression.getNamespace();
        // if a namespcae is present, check if it's 'cdi' or 'inject'
        if (namespace != null && (CDI_NAMESPACE.equals(namespace) || INJECT_NAMESPACE.equals(namespace))) {
            // then it obviously has an inject statement
            return true;
        }
        // else loop over other expression parts
        for (Expression.Part part : expression.getParts()) {
            // is this a virtual mehtod?
            if (part.isVirtualMethod()) {
                // get the paramters
                for (Expression param : part.asVirtualMethod().getParameters()) {
                    // if it is a literal, don't care
                    if (param.isLiteral()) {
                        continue;
                    }
                    // is it an inject expression?
                    if (isInjectExpression(param)) {
                        // then again, it obviously is true
                        return true;
                    }
                }
            }
        }
        // but per default return false
        return false;
    }

    private void processDefaultTemplate(String path, List<Template> templates, QuteProperties config, Engine engine) {
        if (engine.isTemplateLoaded(path)) {
            return;
        }
        for (String suffix : config.suffixes) {
            for (Template template : templates) {
                if (template.getId().endsWith(suffix)) {
                    engine.putTemplate(path, template);
                    return;
                }
            }
        }
    }

    static class ResourceTemplateLocation implements TemplateLocation {

        private final URL resource;
        private final Optional<Variant> variant;

        ResourceTemplateLocation(URL resource, Variant variant) {
            this.resource = resource;
            this.variant = Optional.ofNullable(variant);
        }

        @Override
        public Reader read() {
            Charset charset = null;
            if (variant.isPresent()) {
                charset = variant.get().getCharset();
            }
            if (charset == null) {
                charset = StandardCharsets.UTF_8;
            }
            try {
                return new InputStreamReader(resource.openStream(), charset);
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public Optional<Variant> getVariant() {
            return variant;
        }

    }

    static class ContentTemplateLocation implements TemplateLocation {

        private final String content;
        private final Optional<Variant> variant;

        ContentTemplateLocation(String content, Variant variant) {
            this.content = content;
            this.variant = Optional.ofNullable(variant);
        }

        @Override
        public Reader read() {
            return new StringReader(content);
        }

        @Override
        public Optional<Variant> getVariant() {
            return variant;
        }

    }
}
