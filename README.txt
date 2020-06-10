JOOQ Immutable POJO Record Mapper
---------------------------------

This project supplies a JOOQ RecordMapper which will map records to immutable POJO's annotated with
@ConstructorProperties.

JOOQ's org.jooq.impl.DefaultRecordMapper.ImmutablePOJOMapperWithParameterNames will attempt to invoke
java.lang.reflect.AccessibleObject.setAccessible(true) on a target POJO's fields which match the record,
potentially throwing an InaccessibleObjectException if the target class is part of a JPMS module which
isn't open to JOOQ.

The ImmutablePOJORecordMapper supplied here will simply invoke an accessible constructor without
attempting to modify the target object, so it's module doesn't need to be open to JOOQ.

All other mappings will be deferred to JOOQ's DefaultRecordMapperProvider.


You can use this to replace the DefaultRecordMapperProvider like this:

final DSLContext dslContext = DSL.using(SQLDialect.DEFAULT);
dslContext.configuration().set(new ImmutablePOJORecordMapper(dslContext.configuration()));
