package org.jboss.shamrock.deployment.configuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import io.smallrye.config.SmallRyeConfig;
import org.jboss.protean.gizmo.AssignableResultHandle;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.FieldDescriptor;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;
import org.wildfly.common.Assert;

/**
 * A configuration definition node describing a configuration group.
 */
public class GroupConfigType extends CompoundConfigType {

    private final ClassLoader classLoader;
    private final String className;
    private final Map<String, ConfigType> fields;
    private Class<?> class_;
    private Constructor<?> constructor;
    private Map<String, Field> classFields;

    public GroupConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final ClassLoader classLoader, final String className) {
        super(containingName, container, consumeSegment);
        Assert.checkNotNullParam("className", className);
        Assert.checkNotEmptyParam("className", className);
        this.className = className;
        // classLoader may be {@code null}
        this.classLoader = classLoader;
        this.fields = new HashMap<>();
    }

    public GroupConfigType(final String containingName, final CompoundConfigType container, final boolean consumeSegment, final Class<?> class_) {
        this(containingName, container, consumeSegment, class_.getClassLoader(), class_.getName());
        this.class_ = class_;
    }

    public void load() throws ClassNotFoundException {
        super.load();
        if (class_ == null) {
            class_ = Class.forName(className, false, classLoader);
            try {
                constructor = class_.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Constructor of " + class_ + " is missing");
            }
            if ((constructor.getModifiers() & Modifier.PRIVATE) != 0) {
                throw new IllegalArgumentException("Constructor of " + class_ + " must not be private");
            }
            classFields = new HashMap<>();
            for (Field field : class_.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if ((modifiers & Modifier.STATIC) == 0) {
                    // consider this one
                    if ((modifiers & Modifier.PRIVATE) == 0) {
                        throw new IllegalArgumentException("Field \"" + field.getName() + "\" of " + class_ + " must not be private");
                    }
                    field.setAccessible(true);
                    classFields.put(field.getName(), field);
                }
            }
            if (! classFields.keySet().containsAll(fields.keySet())) {
                final TreeSet<String> missing = new TreeSet<>(fields.keySet());
                missing.removeAll(classFields.keySet());
                throw new IllegalArgumentException("Fields missing from " + class_ + ": " + missing);
            }
            if (! fields.keySet().containsAll(classFields.keySet())) {
                final TreeSet<String> extra = new TreeSet<>(classFields.keySet());
                extra.removeAll(fields.keySet());
                throw new IllegalArgumentException("Extra unknown fields on " + class_ + ": " + extra);
            }
            for (ConfigType node : fields.values()) {
                node.load();
            }
        }
    }

    public ResultHandle writeInitialization(final BytecodeCreator body, final ResultHandle smallRyeConfig) {
        final ResultHandle instance = body.newInstance(MethodDescriptor.ofConstructor(className));
        for (Map.Entry<String, ConfigType> entry : fields.entrySet()) {
            final String fieldName = entry.getKey();
            final ConfigType fieldType = entry.getValue();
            body.writeInstanceField(FieldDescriptor.of(className, fieldName, classFields.get(fieldName).getType()), instance, fieldType.writeInitialization(body, smallRyeConfig));
        }
        return instance;
    }

    public ConfigType getField(String name) {
        return fields.get(name);
    }

    public GroupConfigType addField(ConfigType node) {
        final String containingName = node.getContainingName();
        final ConfigType existing = fields.putIfAbsent(containingName, node);
        if (existing != null) {
            throw new IllegalArgumentException("Cannot add duplicate field \"" + containingName + "\" to " + this);
        }
        return this;
    }

    private Field findField(final String name) {
        if (class_ == null) throw notLoadedException();
        final Field field = classFields.get(name);
        if (field == null) throw new IllegalStateException("Missing field " + name + " on " + class_);
        return field;
    }

    private Object create(final ConfigPropertyName name, final SmallRyeConfig config) {
        Object self;
        try {
            self = constructor.newInstance();
        } catch (InstantiationException e) {
            throw toError(e);
        } catch (IllegalAccessException e) {
            throw toError(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
        for (Map.Entry<String, ConfigType> entry : fields.entrySet()) {
            entry.getValue().getDefaultValueIntoEnclosing(name, self, config);
        }
        return self;
    }

    private ResultHandle generateCreate(final BytecodeCreator body, final ResultHandle name, final ResultHandle config) {
        final ResultHandle self = body.newInstance(MethodDescriptor.ofConstructor(className));
        for (Map.Entry<String, ConfigType> entry : fields.entrySet()) {
            entry.getValue().generateGetDefaultValueIntoEnclosing(body, name, self, config);
        }
        return self;
    }

    Object getChildObject(final ConfigPropertyName name, final SmallRyeConfig config, final Object self, final String childName) {
        final Field field = findField(childName);
        Object val = getFromField(field, self);
        if (val == null) {
            getField(childName).getDefaultValueIntoEnclosing(name, self, config);
            val = getFromField(field, self);
        }
        return val;
    }

    ResultHandle generateGetChildObject(final BytecodeCreator body, final ResultHandle name, final ResultHandle config, final ResultHandle self, final String childName) {
        final AssignableResultHandle val = body.createVariable(findField(childName).getType());
        body.assign(val, body.readInstanceField(FieldDescriptor.of(className, childName, findField(childName).getType()), self));
        final BytecodeCreator isNull = body.ifNull(val).trueBranch();
        getField(childName).generateGetDefaultValueIntoEnclosing(isNull, name, self, config);
        isNull.assign(val, isNull.readInstanceField(FieldDescriptor.of(className, childName, findField(childName).getType()), self));
        return val;
    }

    private static Object getFromField(Field field, Object obj) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }

    Object getOrCreate(final ConfigPropertyName name, final SmallRyeConfig config) {
        final CompoundConfigType container = getContainer();
        final Object enclosing = container.getOrCreate(name.getParent(), config);
        Object self = container.getChildObject(name.getParent(), config, enclosing, getContainingName());
        if (self == null) {
            // it's a map, and it doesn't contain our key.
            self = create(name, config);
            container.setChildObject(enclosing, getContainingName(), self);
        }
        return self;
    }

    ResultHandle generateGetOrCreate(final BytecodeCreator body, final ResultHandle name, final ResultHandle config) {
        final CompoundConfigType container = getContainer();
        if (isConsumeSegment()) body.invokeVirtualMethod(NI_PREV_METHOD, name);
        final ResultHandle enclosing = container.generateGetOrCreate(body, name, config);
        ResultHandle self = container.generateGetChildObject(body, name, config, enclosing, getContainingName());
        if (isConsumeSegment()) body.invokeVirtualMethod(NI_NEXT_METHOD, name);
        if (container instanceof MapConfigType) {
            // it could be null
            final BytecodeCreator createBranch = body.ifNull(self).trueBranch();
            self = generateCreate(body, name, config);
            container.generateSetChildObject(createBranch, enclosing, getContainingName(), self);
        }
        return self;
    }

    public String getClassName() {
        return className;
    }

    void setChildObject(final Object self, final String name, final Object value) {
        try {
            findField(name).set(self, value);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }

    void generateSetChildObject(final BytecodeCreator body, final ResultHandle self, final String name, final ResultHandle value) {
        body.writeInstanceField(FieldDescriptor.of(className, name, findField(name).getType()), self, value);
    }

    void setChildBoolean(final Object obj, final String name, final boolean value) {
        try {
            findField(name).setBoolean(obj, value);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }

    void generateSetChildBoolean(final BytecodeCreator body, final ResultHandle instance, final String name, final ResultHandle booleanValue) {
        body.writeInstanceField(FieldDescriptor.of(className, name, boolean.class), instance, booleanValue);
    }

    void setChildInt(final Object obj, final String name, final int value) {
        try {
            findField(name).setInt(obj, value);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }

    void generateSetChildInt(final BytecodeCreator body, final ResultHandle instance, final String name, final ResultHandle intValue) {
        body.writeInstanceField(FieldDescriptor.of(className, name, int.class), instance, intValue);
    }

    void setChildLong(final Object obj, final String name, final long value) {
        try {
            findField(name).setLong(obj, value);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }

    void setChildFloat(final Object obj, final String name, final float value) {
        try {
            findField(name).setFloat(obj, value);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }

    void setChildDouble(final Object obj, final String name, final double value) {
        try {
            findField(name).setDouble(obj, value);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }

    void getDefaultValueIntoEnclosing(final ConfigPropertyName name, final Object enclosing, final SmallRyeConfig config) {
        getContainer().setChildObject(enclosing, getContainingName(), create(name, config));
    }

    void generateGetDefaultValueIntoEnclosing(final BytecodeCreator body, final ResultHandle name, final ResultHandle enclosing, final ResultHandle config) {
        getContainer().generateSetChildObject(body, enclosing, getContainingName(), generateCreate(body, name, config));
    }
}
