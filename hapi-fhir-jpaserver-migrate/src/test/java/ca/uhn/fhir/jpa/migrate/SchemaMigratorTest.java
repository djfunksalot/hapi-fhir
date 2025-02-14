package ca.uhn.fhir.jpa.migrate;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.jpa.migrate.taskdef.AddTableRawSqlTask;
import ca.uhn.fhir.jpa.migrate.taskdef.BaseTest;
import com.google.common.collect.ImmutableList;
import org.flywaydb.core.api.FlywayException;
import org.hamcrest.Matchers;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

public class SchemaMigratorTest extends BaseTest {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SchemaMigratorTest.class);

	@Test
	public void testMigrationRequired() {
		SchemaMigrator schemaMigrator = createTableMigrator();

		try {
			schemaMigrator.validate();
			fail();
		} catch (ConfigurationException e) {
			assertEquals("The database schema for " + getUrl() + " is out of date.  Current database schema version is unknown.  Schema version required by application is 1.1.  Please run the database migrator.", e.getMessage());
		}

		schemaMigrator.migrate();

		schemaMigrator.validate();
	}


	@Test
	public void testRepairFailedMigration() {
		SchemaMigrator schemaMigrator = createSchemaMigrator("SOMETABLE", "create fable SOMETABLE (PID bigint not null, TEXTCOL varchar(255))", "1");
		try {
			schemaMigrator.migrate();
			fail();
		} catch (FlywayException e) {
			assertEquals(org.springframework.jdbc.BadSqlGrammarException.class, e.getCause().getCause().getClass());
		}
		schemaMigrator = createTableMigrator();
		schemaMigrator.migrate();
	}

	@Test
	public void testOutOfOrderMigration() {
		SchemaMigrator schemaMigrator = createSchemaMigrator("SOMETABLE", "create table SOMETABLE (PID bigint not null, TEXTCOL varchar(255))", "2");
		schemaMigrator.migrate();

		schemaMigrator = createSchemaMigrator("SOMETABLE" ,"create table SOMEOTHERTABLE (PID bigint not null, TEXTCOL varchar(255))", "1");

		try {
			schemaMigrator.migrate();
			fail();
		} catch (FlywayException e) {
			assertThat(e.getMessage(), containsString("Detected resolved migration not applied to database: 1.1"));
		}
		schemaMigrator.setOutOfOrderPermitted(true);
		schemaMigrator.migrate();
	}


	@Test
	public void testMigrationRequiredNoFlyway() throws SQLException {
		SchemaMigrator schemaMigrator = createTableMigrator();
		schemaMigrator.setDriverType(DriverTypeEnum.H2_EMBEDDED);
		schemaMigrator.setDontUseFlyway(true);

		// Validate shouldn't fail if we aren't using Flyway
		schemaMigrator.validate();

		schemaMigrator.migrate();

		schemaMigrator.validate();

		DriverTypeEnum.ConnectionProperties connectionProperties = DriverTypeEnum.H2_EMBEDDED.newConnectionProperties(getDataSource().getUrl(), getDataSource().getUsername(), getDataSource().getPassword());
		Set<String> tableNames = JdbcUtils.getTableNames(connectionProperties);
		assertThat(tableNames, Matchers.contains("SOMETABLE"));

	}

	@Nonnull
	private SchemaMigrator createTableMigrator() {
		return createSchemaMigrator("SOMETABLE", "create table SOMETABLE (PID bigint not null, TEXTCOL varchar(255))", "1");
	}

	@Nonnull
	private SchemaMigrator createSchemaMigrator(String theTableName, String theSql, String theSchemaVersion) {
		AddTableRawSqlTask task = new AddTableRawSqlTask("1", theSchemaVersion);
		task.setTableName(theTableName);
		task.addSql(DriverTypeEnum.H2_EMBEDDED, theSql);
		SchemaMigrator retval = new SchemaMigrator(SchemaMigrator.HAPI_FHIR_MIGRATION_TABLENAME, getDataSource(), new Properties(), ImmutableList.of(task));
		retval.setDriverType(DriverTypeEnum.H2_EMBEDDED);
		return retval;
	}
}
