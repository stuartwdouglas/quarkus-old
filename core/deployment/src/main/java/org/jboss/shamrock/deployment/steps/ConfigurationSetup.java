package org.jboss.shamrock.deployment.steps;

import static org.jboss.shamrock.deployment.util.ReflectUtil.*;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.graalvm.nativeimage.ImageInfo;
import org.jboss.protean.gizmo.BranchResult;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.ClassCreator;
import org.jboss.protean.gizmo.ClassOutput;
import org.jboss.protean.gizmo.DescriptorUtils;
import org.jboss.protean.gizmo.FieldDescriptor;
import org.jboss.protean.gizmo.MethodCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;
import org.jboss.shamrock.deployment.annotations.BuildStep;
import org.jboss.shamrock.deployment.annotations.ExecutionTime;
import org.jboss.shamrock.deployment.builditem.ConfigurationBuildItem;
import org.jboss.shamrock.deployment.builditem.ConfigurationRegistrationBuildItem;
import org.jboss.shamrock.deployment.builditem.ConfigurationRunTimeKeyBuildItem;
import org.jboss.shamrock.deployment.builditem.GeneratedClassBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.RuntimeInitializedClassBuildItem;
import org.jboss.shamrock.deployment.configuration.BooleanConfigType;
import org.jboss.shamrock.deployment.configuration.CompoundConfigType;
import org.jboss.shamrock.deployment.configuration.ConfigDefinition;
import org.jboss.shamrock.deployment.configuration.ConfigPatternMap;
import org.jboss.shamrock.deployment.configuration.GroupConfigType;
import org.jboss.shamrock.deployment.configuration.IntConfigType;
import org.jboss.shamrock.deployment.configuration.LeafConfigType;
import org.jboss.shamrock.deployment.configuration.MapConfigType;
import org.jboss.shamrock.deployment.configuration.ObjectConfigType;
import org.jboss.shamrock.deployment.configuration.ObjectListConfigType;
import org.jboss.shamrock.deployment.configuration.OptionalObjectConfigType;
import org.jboss.shamrock.deployment.util.StringUtil;
import org.jboss.shamrock.runtime.annotations.ConfigGroup;
import org.jboss.shamrock.runtime.annotations.ConfigItem;
import org.jboss.shamrock.runtime.configuration.ExpandingConfigSource;
import org.jboss.shamrock.runtime.configuration.NameIterator;
import org.jboss.shamrock.runtime.configuration.SimpleConfigurationProviderResolver;
import org.objectweb.asm.Opcodes;

/**
 * Setup steps for configuration purposes.
 */
public class ConfigurationSetup {

    public static final String NO_CONTAINING_NAME = "<<ignored>>";
    public static final String MAIN_CONFIG_HELPER = "org.jboss.shamrock.runtime.generated.MainConfigHelper";
    public static final String STATIC_INIT_CONFIG_HELPER = "org.jboss.shamrock.runtime.generated.StaticInitConfigHelper";

    public ConfigurationSetup() {}

    /**
     * Run before anything that consumes configuration; sets up the main configuration definition instance.
     *
     * @param rootItems the registered root items
     * @return the configuration build item
     */
    @BuildStep
    public ConfigurationBuildItem initializeConfiguration(List<ConfigurationRegistrationBuildItem> rootItems) {
        final ConfigDefinition configDefinition = new ConfigDefinition();
        for (ConfigurationRegistrationBuildItem rootItem : rootItems) {
            final String baseKey = rootItem.getBaseKey();
            final Type type = rootItem.getType();
            // parse out the type
            final Class<?> rawType = rawTypeOf(type);
            if (rawType == Map.class) {
                // check key type
                processMap(NO_CONTAINING_NAME, configDefinition, true, baseKey, type);
            } else if (rawType.isAnnotationPresent(ConfigGroup.class)) {
                processConfigGroup(NO_CONTAINING_NAME, configDefinition, true, baseKey, rawType);
            }

        }

        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder();
        // expand properties
        builder.withWrapper(ExpandingConfigSource::new);
        builder.addDefaultSources();
        final SmallRyeConfig src = (SmallRyeConfig) builder.build();
        configDefinition.loadConfiguration(src);
        return new ConfigurationBuildItem(configDefinition);
    }

    private GroupConfigType processConfigGroup(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String baseKey, final Class<?> configGroupClass) {
        GroupConfigType gct = new GroupConfigType(containingName, container, consumeSegment, configGroupClass);
        final Field[] fields = configGroupClass.getDeclaredFields();
        for (Field field : fields) {
            final ConfigItem configItemAnnotation = field.getAnnotation(ConfigItem.class);
            final String name = configItemAnnotation == null ? ConfigItem.HYPHENATED_ELEMENT_NAME : configItemAnnotation.name();
            String subKey;
            boolean consume;
            if (name.equals(ConfigItem.PARENT)) {
                subKey = baseKey;
                consume = false;
            } else if (name.equals(ConfigItem.ELEMENT_NAME)) {
                subKey = baseKey + "." + field.getName();
                consume = true;
            } else if (name.equals(ConfigItem.HYPHENATED_ELEMENT_NAME)) {
                subKey = baseKey + "." + StringUtil.hyphenate(field.getName());
                consume = true;
            } else {
                subKey = baseKey + "." + name;
                consume = true;
            }
            final String defaultValue = configItemAnnotation == null ? ConfigItem.NO_DEFAULT : configItemAnnotation.defaultValue();
            final Type fieldType = field.getGenericType();
            final Class<?> fieldClass = field.getType();
            if (fieldClass.isAnnotationPresent(ConfigGroup.class)) {
                if (! defaultValue.equals(ConfigItem.NO_DEFAULT)) {
                    throw new IllegalArgumentException("Default value cannot be given for a config group");
                }
                gct.addField(processConfigGroup(field.getName(), gct, consume, subKey, fieldClass));
            } else if (fieldClass.isPrimitive()) {
                if (fieldClass == boolean.class) {
                    gct.addField(new BooleanConfigType(field.getName(), gct, consume, defaultValue));
                } else if (fieldClass == int.class) {
                    gct.addField(new IntConfigType(field.getName(), gct, consume, defaultValue));
                } else {
                    throw new IllegalArgumentException("Unsupported primitive field type on " + field + " of " + configGroupClass);
                }
            } else if (fieldClass == Map.class) {
                if (rawTypeOfParameter(fieldType, 0) != String.class) {
                    throw new IllegalArgumentException("Map key must be " + String.class + " on field " + field + " of configuration " + configGroupClass);
                }
                gct.addField(processMap(field.getName(), gct, consume, subKey, typeOfParameter(fieldType, 1)));
            } else if (fieldClass == List.class) {
                // list leaf class
                final Class<?> listType = rawTypeOfParameter(fieldType, 0);
                gct.addField(new ObjectListConfigType(field.getName(), gct, consume, defaultValue, listType));
            } else if (fieldClass == Optional.class) {
                // optional config property
                gct.addField(new OptionalObjectConfigType(field.getName(), gct, consume, defaultValue, rawTypeOfParameter(fieldType, 0)));
            } else {
                // it's a plain config property
                gct.addField(new ObjectConfigType(field.getName(), gct, consume, defaultValue, fieldClass));
            }
        }
        return gct;
    }

    private MapConfigType processMap(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final String baseKey, final Type mapValueType) {
        MapConfigType mct = new MapConfigType(containingName, container, consumeSegment);
        final Class<?> valueClass = rawTypeOf(mapValueType);
        if (valueClass == Map.class) {
            processMap(NO_CONTAINING_NAME, mct, true, baseKey + ".{**}", typeOfParameter(mapValueType, 1));
        } else if (valueClass.isAnnotationPresent(ConfigGroup.class)) {
            processConfigGroup(NO_CONTAINING_NAME, mct, true, baseKey + ".{**}", valueClass);
        } else if (valueClass == List.class) {
            // todo: into pattern map
            new ObjectListConfigType(NO_CONTAINING_NAME, mct, consumeSegment, "", rawTypeOfParameter(typeOfParameter(mapValueType, 1), 0));
        } else if (valueClass == Optional.class || valueClass == OptionalInt.class || valueClass == OptionalDouble.class || valueClass == OptionalLong.class) {
            throw new IllegalArgumentException("Optionals are not allowed as a map value type");
        } else {
            // treat as a plain object, hope for the best
            new ObjectConfigType(NO_CONTAINING_NAME, mct, true, "", valueClass);
        }
        return mct;
    }

    /**
     * Generate the bytecode to load configuration objects at static init and run time.
     *
     * @param configurationBuildItem the config build item
     * @param runTimeKeys the list of configuration group/map keys to make available
     * @param classConsumer the consumer of generated classes
     * @param runTimeInitConsumer the consumer of runtime init classes
     */
    @BuildStep
    void finalizeConfigLoader(
        ConfigurationBuildItem configurationBuildItem,
        List<ConfigurationRunTimeKeyBuildItem> runTimeKeys,
        Consumer<GeneratedClassBuildItem> classConsumer,
        Consumer<RuntimeInitializedClassBuildItem> runTimeInitConsumer
    ) {
        Map<CompoundConfigType, ExecutionTime> timeByType = new IdentityHashMap<>();
        Map<CompoundConfigType, String> addressByType = new IdentityHashMap<>();
        for (ConfigurationRunTimeKeyBuildItem runTimeKey : runTimeKeys) {
            final String baseAddress = runTimeKey.getBaseAddress();
            final CompoundConfigType type = runTimeKey.getExpectedType();
            addressByType.put(type, baseAddress);
            final ExecutionTime executionTime = runTimeKey.getExecutionTime();
            if (executionTime == ExecutionTime.RUNTIME_INIT) {
                // stronger than static
                timeByType.put(type, ExecutionTime.RUNTIME_INIT);
            } else {
                // don't override run time
                timeByType.putIfAbsent(type, ExecutionTime.STATIC_INIT);
            }
        }

        final ClassOutput classOutput = new ClassOutput() {
            public void write(final String name, final byte[] data) {
                classConsumer.accept(new GeneratedClassBuildItem(true, name, data));
            }
        };
        // Get the set of run time and static init leaf keys
        final ConfigDefinition configDefinition = configurationBuildItem.getConfigDefinition();
        final ConfigPatternMap<LeafConfigType> allLeafPatterns = configDefinition.getLeafPatterns();
        final ConfigPatternMap<LeafConfigType> staticKeys = new ConfigPatternMap<>();
        final ConfigPatternMap<LeafConfigType> mainKeys = new ConfigPatternMap<>();
        for (final LeafConfigType leafConfigType : allLeafPatterns) {
            for (CompoundConfigType nextRootType = leafConfigType.getNextRootType(); nextRootType != null; nextRootType = nextRootType.getNextRootType()) {
                final ExecutionTime executionTime = timeByType.get(nextRootType);
                if (executionTime != null) {
                    staticKeys.addPattern(addressByType.get(nextRootType), leafConfigType);
                    if (executionTime == ExecutionTime.RUNTIME_INIT) {
                        mainKeys.addPattern(addressByType.get(nextRootType), leafConfigType);
                    }
                    break;
                }
            }
        }

        // create both classes at once
        try (
            final ClassCreator mcc = new ClassCreator(classOutput, MAIN_CONFIG_HELPER, null, Object.class.getName());
            final ClassCreator scc = new ClassCreator(classOutput, STATIC_INIT_CONFIG_HELPER, null, Object.class.getName())
        ) {
            final MethodDescriptor createAndRegisterConfig;
            // config object initialization
            // this has to be on the static init class, which is visible at both static init and execution time
            try (MethodCreator carc = scc.getMethodCreator("createAndRegisterConfig", SmallRyeConfig.class)) {
                carc.setModifiers(Opcodes.ACC_STATIC);
                final ResultHandle builder = carc.newInstance(MethodDescriptor.ofConstructor(SmallRyeConfigBuilder.class));
                carc.invokeVirtualMethod(MethodDescriptor.ofMethod(SmallRyeConfigBuilder.class, "addDefaultSources", SmallRyeConfigBuilder.class), builder);
                // todo: add custom converters, sources
                // todo: add expression-resolving wrapper
                final ResultHandle config = carc.invokeVirtualMethod(MethodDescriptor.ofMethod(SmallRyeConfigBuilder.class, "build", SmallRyeConfig.class), builder);
                final ResultHandle providerResolver = carc.newInstance(MethodDescriptor.ofConstructor(SimpleConfigurationProviderResolver.class, Config.class), config);
                carc.invokeStaticMethod(MethodDescriptor.ofMethod(ConfigProviderResolver.class, "setInstance", ConfigProviderResolver.class), providerResolver);
                carc.returnValue(carc.checkCast(config, SmallRyeConfig.class));
                createAndRegisterConfig = carc.getMethodDescriptor();
            }

            // static init blocks for both classes
            try (
                MethodCreator mccInit = mcc.getMethodCreator("<clinit>", void.class);
                MethodCreator sccInit = scc.getMethodCreator("<clinit>", void.class)
            ) {
                final ResultHandle sccConfig = sccInit.invokeStaticMethod(createAndRegisterConfig);
                for (ConfigurationRunTimeKeyBuildItem runTimeKey : runTimeKeys) {
                    String typeDescr = DescriptorUtils.extToInt(runTimeKey.getExpectedType().getClassName());
                    // first add a field for it
                    String fieldName = fieldify(runTimeKey.getBaseAddress());
                    scc.getFieldCreator(fieldName, typeDescr).setModifiers(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

                    // initialize all fields to default values first
                    final MethodDescriptor initMethodDescr = MethodDescriptor.ofMethod(STATIC_INIT_CONFIG_HELPER, "init:" + fieldName, typeDescr);
                    sccInit.writeStaticField(
                        FieldDescriptor.of(STATIC_INIT_CONFIG_HELPER, fieldName, typeDescr),
                        sccInit.invokeStaticMethod(initMethodDescr)
                    );

                    // write initialization method
                    try (MethodCreator initMethod = scc.getMethodCreator(initMethodDescr)) {
                        initMethod.returnValue(runTimeKey.getExpectedType().writeInitialization(initMethod, sccConfig));
                    }

                    if (runTimeKey.getExecutionTime() == ExecutionTime.RUNTIME_INIT) {
                        mcc.getFieldCreator(fieldName, typeDescr).setModifiers(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
                        // copy default from static init phase
                        mccInit.writeStaticField(FieldDescriptor.of(MAIN_CONFIG_HELPER, fieldName, typeDescr), mccInit.readStaticField(FieldDescriptor.of(STATIC_INIT_CONFIG_HELPER, fieldName, typeDescr)));
                    }
                }
                // now write out the parsing
                writeParsing(sccInit, sccConfig, staticKeys);
                final BranchResult mccIfImage = mccInit.ifNonZero(mccInit.invokeStaticMethod(MethodDescriptor.ofMethod(ImageInfo.class, "inImageRuntimeCode", boolean.class)));
                try (BytecodeCreator mccIsImage = mccIfImage.trueBranch()) {
                    final ResultHandle mccConfig = mccIsImage.invokeStaticMethod(createAndRegisterConfig);
                    writeParsing(mccIsImage, mccConfig, mainKeys);
                }
                sccInit.returnValue(sccInit.loadNull());
                mccInit.returnValue(mccInit.loadNull());
            }
        }

        runTimeInitConsumer.accept(new RuntimeInitializedClassBuildItem(MAIN_CONFIG_HELPER));
    }

    private void writeParsing(final BytecodeCreator body, final ResultHandle config, final ConfigPatternMap<LeafConfigType> keyMap) {
        // setup
        // Iterable iterable = config.getPropertyNames();
        final ResultHandle iterable = body.invokeVirtualMethod(MethodDescriptor.ofMethod(SmallRyeConfig.class, "getPropertyNames", Iterable.class), config);
        // Iterator iterator = iterable.iterator();
        final ResultHandle iterator = body.invokeInterfaceMethod(MethodDescriptor.ofMethod(Iterable.class, "iterator", Iterator.class), iterable);

        // loop: {
        try (BytecodeCreator loop = body.createScope()) {
            // if (iterator.hasNext())
            final BranchResult ifHasNext = loop.ifNonZero(iteratorHasNext(loop, iterator));
            // {
            try (BytecodeCreator hasNext = ifHasNext.trueBranch()) {
                // key = iterator.next();
                final ResultHandle key = hasNext.checkCast(iteratorNext(hasNext, iterator), String.class);
                // NameIterator keyIter = new NameIterator(key);
                final ResultHandle keyIter = hasNext.newInstance(MethodDescriptor.ofConstructor(NameIterator.class, String.class), key);
                // if (! keyIter.hasNext()) continue loop;
                hasNext.ifNonZero(nameIteratorHasNext(hasNext, keyIter)).falseBranch().continueScope(loop);
                // keyIter.next();
                hasNext.invokeVirtualMethod(MethodDescriptor.ofMethod(NameIterator.class, "next", void.class), keyIter);
                // if (! keyIter.segmentEquals("shamrock")) continue loop;
                hasNext.ifNonZero(genSegmentEquals(body, keyIter, "shamrock")).falseBranch().continueScope(loop);
                generateParserBody(body, config, keyIter, keyMap, loop, key);
                // continue loop;
                hasNext.continueScope(loop);
            }
            // }
        }
        // }
        body.returnValue(body.loadNull());
    }

    private void generateParserBody(final BytecodeCreator body, final ResultHandle config, final ResultHandle keyIter, final ConfigPatternMap<LeafConfigType> keyMap, final BytecodeCreator loop, final ResultHandle key) {
        final LeafConfigType matched = keyMap.getMatched();
        if (matched != null) {
            // if (! keyIter.hasNext()) {
            try (BytecodeCreator matchedBody = body.ifNonZero(nameIteratorHasNext(body, keyIter)).falseBranch()) {
                // (exact match generated code)
                matched.generateAcceptConfigurationValue(matchedBody, key, config);
                // continue loop;
                matchedBody.continueScope(loop);
            }
            // }
        }
        // branches for each next-string
        // keyIter.next();
        nameIteratorNext(body, keyIter);
        final Iterable<String> names = keyMap.childNames();
        for (String name : names) {
            if (name.equals(ConfigPatternMap.WC_SINGLE) || name.equals(ConfigPatternMap.WC_MULTI)) {
                // skip
            } else {
                // TODO: string switch
                // if (keyIter.segmentEquals(name)) {
                try (BytecodeCreator nameMatched = body.ifNonZero(genSegmentEquals(body, keyIter, name)).trueBranch()) {
                    // (generated recursive)
                    generateParserBody(nameMatched, config, keyIter, keyMap.getChild(name), loop, key);
                    // continue loop;
                    nameMatched.continueScope(loop);
                }
                // }
            }
        }
        // todo: unknown name warning goes here
    }

    private static ResultHandle genSegmentEquals(final BytecodeCreator body, final ResultHandle nameItr, final String name) {
        return body.invokeVirtualMethod(MethodDescriptor.ofMethod(NameIterator.class, "segmentEquals", boolean.class, String.class), nameItr, body.load(name));
    }

    private static ResultHandle iteratorHasNext(final BytecodeCreator bc, final ResultHandle iterator) {
        return bc.invokeInterfaceMethod(MethodDescriptor.ofMethod(Iterator.class, "hasNext", boolean.class), iterator);
    }

    private static ResultHandle iteratorNext(final BytecodeCreator bc, final ResultHandle iterator) {
        return bc.invokeInterfaceMethod(MethodDescriptor.ofMethod(Iterator.class, "next", Object.class), iterator);
    }

    private static ResultHandle nameIteratorHasNext(final BytecodeCreator bc, final ResultHandle nameItr) {
        return bc.invokeVirtualMethod(MethodDescriptor.ofMethod(NameIterator.class, "hasNext", boolean.class), nameItr);
    }

    private static void nameIteratorNext(final BytecodeCreator bc, final ResultHandle nameItr) {
        bc.invokeVirtualMethod(MethodDescriptor.ofMethod(NameIterator.class, "next", void.class), nameItr);
    }

    private static String fieldify(final String baseAddress) {
        final int length = baseAddress.length();
        assert length > 0;
        StringBuilder b = new StringBuilder(length + (length >> 1));
        for (int i = 0; i < length; i = baseAddress.offsetByCodePoints(i, 1)) {
            fieldifyQuote(b, baseAddress.codePointAt(i));
        }
        return b.toString();
    }

    private static void fieldifyQuote(final StringBuilder b, final int cp) {
        switch (cp) {
            case '$': b.append("$$"); return;
            case '.': b.append("_"); return;
            case '_': b.append("$_"); return;
            case ';': b.append("$:"); return;
            case '[': b.append("$<"); return;
            case '/': b.append("$\\"); return;
            default: b.appendCodePoint(cp);
        }
    }

    @BuildStep
    void writeDefaultConfiguration(

    ) {

    }
}
