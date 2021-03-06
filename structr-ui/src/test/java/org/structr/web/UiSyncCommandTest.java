package org.structr.web;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.Matchers.equalTo;
import org.structr.common.error.FrameworkException;
import org.structr.core.graph.ClearDatabase;
import org.structr.core.graph.Tx;
import org.structr.dynamic.File;
import org.structr.web.common.StructrUiTest;
import org.structr.web.entity.FileBase;
import org.structr.web.entity.dom.Content;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.Page;
import org.structr.web.entity.html.Head;
import org.structr.web.entity.html.Script;

/**
 *
 * @author Christian Morgner
 */
public class UiSyncCommandTest extends StructrUiTest {

	public void testExportErrors() {

		RestAssured
			.given()
			.header("X-User", "superadmin")
			.header("X-Password", "sehrgeheim")
			.body("{}")
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
			.response()
			.contentType("application/json")
			.statusCode(400)
			.body("code", equalTo(400))
			.body("message", equalTo("Please specify mode, must be one of (import|export)"))
			.when()
			.post("/maintenance/syncUi");

		RestAssured
			.given()
			.header("X-User", "superadmin")
			.header("X-Password", "sehrgeheim")
			.body("{ mode: export }")
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
			.response()
			.contentType("application/json")
			.statusCode(400)
			.body("code", equalTo(400))
			.body("message", equalTo("Please specify file name using the file parameter."))
			.when()
			.post("/maintenance/syncUi");

	}

	public void testSimpleExportRoundtrip() {

		final String fileName = super.basePath + "/exportTest.zip";
		Page testPage         = null;
		Head head             = null;
		File textFile         = null;
		File jsFile           = null;

		try (final Tx tx = app.tx()) {

			testPage = Page.createSimplePage(securityContext, "TestPage");
			head     = app.nodeQuery(Head.class).getFirst();
			textFile = app.create(File.class, "testfile.txt");
			jsFile   = app.create(File.class, "test.js");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			textFile.setProperty(File.contentType, "text/plain");
			IOUtils.write("This is a test file", textFile.getOutputStream());

			jsFile.setProperty(File.contentType, "application/javascript");
			IOUtils.write("function test() {\n\tconsole.log('Test!');\n}", jsFile.getOutputStream());

			// link script to JS file
			final Script script = (Script)testPage.createElement("script");
			script.setProperty(Script._src, "${link.name}?${link.version}");
			script.setProperty(Script.linkable, jsFile);

			// link script into head
			head.appendChild(script);

			tx.success();

		} catch (FrameworkException | IOException fex) {

			fex.printStackTrace();
			fail("Unexpected exception");
		}


		// verify that the database contains the expected number of nodes
		try (final Tx tx = app.tx()) {

			assertEquals("Database should contain 1 page",           1, app.nodeQuery(Page.class).getAsList().size());
			assertEquals("Database should contain 10 DOM nodes",    11, app.nodeQuery(DOMNode.class).getAsList().size());
			assertEquals("Database should contain 3 content nodes",  3, app.nodeQuery(Content.class).getAsList().size());
			assertEquals("Database should contain 2 files",          2, app.nodeQuery(FileBase.class).getAsList().size());

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception");
		}


		// do export
		RestAssured
			.given()
			.header("X-User", "superadmin")
			.header("X-Password", "sehrgeheim")
			.body("{ mode: export, file: '" + fileName + "' }")
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
			.response()
			.contentType("application/json")
			.statusCode(200)
			.when()
			.post("/maintenance/syncUi");


		// export done, now clean database
		try {
			app.command(ClearDatabase.class).execute();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception");
		}


		// verify that the database is empty
		try (final Tx tx = app.tx()) {

			assertEquals("Database should contain no pages",         0, app.nodeQuery(Page.class).getAsList().size());
			assertEquals("Database should contain no DOM nodes",     0, app.nodeQuery(DOMNode.class).getAsList().size());
			assertEquals("Database should contain no content nodes", 0, app.nodeQuery(Content.class).getAsList().size());
			assertEquals("Database should contain no files",         0, app.nodeQuery(FileBase.class).getAsList().size());

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception");
		}


		// do import
		RestAssured
			.given()
			.header("X-User", "superadmin")
			.header("X-Password", "sehrgeheim")
			.body("{ mode: import, file: '" + fileName + "' }")
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
			.response()
			.contentType("application/json")
			.statusCode(200)
			.when()
			.post("/maintenance/syncUi");


		// verify that the database contains the expected number of nodes
		// (only one file is expected to be imported because it is
		// referenced in the page)
		try (final Tx tx = app.tx()) {

			assertEquals("Database should contain 1 page",           1, app.nodeQuery(Page.class).getAsList().size());
			assertEquals("Database should contain 10 DOM nodes",    11, app.nodeQuery(DOMNode.class).getAsList().size());
			assertEquals("Database should contain 3 content nodes",  3, app.nodeQuery(Content.class).getAsList().size());
			assertEquals("Database should contain 1 file",           1, app.nodeQuery(FileBase.class).getAsList().size());

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception");
		}
	}
}
