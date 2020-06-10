/*
 * This work is licensed under the terms of the Apache License, Version 2.0.
 */

package com.hubick.util.jooq;

import java.beans.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

import org.jooq.*;
import org.jooq.exception.*;
import org.jooq.impl.*;
import org.jooq.tools.*;


/**
 * A {@link RecordMapperProvider} which can map a {@link org.jooq.Record Record} to an
 * {@linkplain Constructor#canAccess(Object) accessible} POJO constructor which has been annotated with
 * {@link ConstructorProperties}. This class will defer all other mapping to the {@link DefaultRecordMapperProvider},
 * and, unlike the {@link org.jooq.impl.DefaultRecordMapper.ImmutablePOJOMapperWithParameterNames
 * ImmutablePOJOMapperWithParameterNames}, does not attempt to
 * {@linkplain java.lang.reflect.Field#setAccessible(boolean) modify} mapped object fields (potentially throwing in an
 * {@link InaccessibleObjectException}).
 */
public class ImmutablePOJORecordMapper implements RecordMapperProvider {
  protected final Configuration configuration;

  /**
   * Construct a new {@link ImmutablePOJORecordMapper}.
   * 
   * @param configuration The {@link Configuration} for this mapper.
   */
  public ImmutablePOJORecordMapper(final Configuration configuration) {
    this.configuration = Objects.requireNonNull(configuration); // The Configuration is critical for DefaultRecordMapperProvider's cache.
    return;
  }

  @Override
  public final <R extends org.jooq.Record,E> RecordMapper<R,E> provide(final RecordType<R> rowType, final Class<? extends E> type) {
    // Immediately defer anything we know won't have ConstructorProperties...
    if ((type.isAnnotation()) || (type.isArray()) || (type.isInterface()) || (type.isPrimitive())) return new DefaultRecordMapperProvider(configuration) {}.provide(rowType, type);

    // See if this type has a Constructor annotated with @ConstructorProperties...
    @SuppressWarnings("unchecked")
    final Optional<Constructor<E>> constructor = Stream.of(((Constructor<E>[])type.getDeclaredConstructors()))
        .filter((c) -> c.canAccess(null))
        .filter((c) -> c.getAnnotation(ConstructorProperties.class) != null)
        .findAny();

    // Hand off everything else to the DefaultRecordMapperProvider...
    if (constructor.isEmpty()) return new DefaultRecordMapperProvider(configuration) {}.provide(rowType, type);

    // What does the @ConstructorProperties tell us...
    final List<String> constructorParamNames = Arrays.asList(constructor.get().getAnnotation(ConstructorProperties.class).value());
    final org.jooq.Field<?>[] recordFields = rowType.fields();

    // Figure out which Field from a Record should be supplied for each Constructor parameter...
    final Integer[] recordFieldToConstructorParamIndexMapping = new Integer[recordFields.length];
    for (int recordFieldIndex = 0; recordFieldIndex < recordFields.length; recordFieldIndex++) {
      for (int constructorParamIndex = 0; constructorParamIndex < constructorParamNames.size(); constructorParamIndex++) {
        if (recordFields[recordFieldIndex].getName().equals(constructorParamNames.get(constructorParamIndex)) || StringUtils.toCamelCaseLC(recordFields[recordFieldIndex].getName()).equals(constructorParamNames.get(constructorParamIndex))) {
          recordFieldToConstructorParamIndexMapping[recordFieldIndex] = constructorParamIndex;
          break;
        }
      }
    }

    return new RecordMapper<R,E>() {

      @Override
      public E map(final R record) throws MappingException {
        try {

          // Use the mapping to build an array of constructor parameters from this Record instance...
          final Object[] constructorParams = new Object[constructor.get().getParameterTypes().length];
          for (int recordFieldIndex = 0; recordFieldIndex < recordFields.length; recordFieldIndex++) {
            if (recordFieldToConstructorParamIndexMapping[recordFieldIndex] != null) constructorParams[recordFieldToConstructorParamIndexMapping[recordFieldIndex]] = record.get(recordFieldIndex);
          }

          // Convert the parameters and invoke the constructor...
          return constructor.get().newInstance(Convert.convert(constructorParams, constructor.get().getParameterTypes()));

        } catch (Exception e) {
          throw new MappingException(e.getClass().getSimpleName() + " while mapping Record to " + type, e);
        }
      }

    };
  }

}
