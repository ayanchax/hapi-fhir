package ca.uhn.fhir.narrative;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.map.LazyMap;
import org.hamcrest.core.StringContains;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.utilities.liquid.DefaultLiquidNarrativeGenerator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.messageresolver.StandardMessageResolver;
import org.thymeleaf.templateresource.ITemplateResource;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DefaultLiquidNarrativeGeneratorR4Test {
	private static FhirContext ourFhirContext = FhirContext.forR4();
	private static final Logger ourLog = LoggerFactory.getLogger(DefaultLiquidNarrativeGeneratorR4Test.class);
	private DefaultLiquidNarrativeGenerator myNarrativeGenerator;

	@Before
	public void before() {
		myNarrativeGenerator = new DefaultLiquidNarrativeGenerator();
		ourFhirContext.setNarrativeGenerator(myNarrativeGenerator);
	}

	@Test
	@Ignore
	public void testGeneratePatient() throws DataFormatException {
		Patient value = new Patient();

		value.addIdentifier().setSystem("urn:names").setValue("123456");
		value.addName().setFamily("blow").addGiven("joe").addGiven((String) null).addGiven("john");
		//@formatter:off
		value.addAddress()
			.addLine("123 Fake Street").addLine("Unit 1")
			.setCity("Toronto").setState("ON").setCountry("Canada");
		//@formatter:on

		value.setBirthDate(new Date());

		Narrative narrative = new Narrative();
		myNarrativeGenerator.generateNarrative(ourFhirContext, value, narrative);
		String output = narrative.getDiv().getValueAsString();
		ourLog.info(output);
		assertThat(output, StringContains.containsString("<div class=\"hapiHeaderText\">joe john <b>BLOW </b></div>"));

	}

	// FIXME KHS see
	@Test
	@Ignore
	public void testTranslations() throws DataFormatException {
		CustomThymeleafNarrativeGenerator customGen = new CustomThymeleafNarrativeGenerator("classpath:/testnarrativee.properties");

		FhirContext ctx = FhirContext.forR4();
		ctx.setNarrativeGenerator(customGen);

		Patient value = new Patient();

		value.addIdentifier().setSystem("urn:names").setValue("123456");
		value.addName().setFamily("blow").addGiven("joe").addGiven((String) null).addGiven("john");
		//@formatter:off
		value.addAddress()
			.addLine("123 Fake Street").addLine("Unit 1")
			.setCity("Toronto").setState("ON").setCountry("Canada");
		//@formatter:on

		value.setBirthDate(new Date());

		Transformer transformer = new Transformer() {

			@Override
			public Object transform(Object input) {
				return "UNTRANSLATED:" + input;
			}};

		Map translations = new HashMap<>();
		translations.put("some_text", "Some beautiful proze");

		customGen.setMessageResolver(new StandardMessageResolver() {
			@Override
			protected Map<String, String> resolveMessagesForTemplate(String template,
			                                                         ITemplateResource templateResource, Locale locale) {
				return LazyMap.decorate(translations, transformer);
			}
		});

		Narrative narrative = new Narrative();
// FIXME KHS use template system provided by Thymeleaf
		//		customGen.generateNarrative(ctx, value, narrative);
		String output = narrative.getDiv().getValueAsString();
		ourLog.info(output);
		assertThat(output, StringContains.containsString("Some beautiful proze"));
		assertThat(output, StringContains.containsString("UNTRANSLATED:other_text"));
	}

	@Test
	@Ignore
	public void testGenerateDiagnosticReport() throws DataFormatException {
		DiagnosticReport value = new DiagnosticReport();
		value.getCode().setText("Some Diagnostic Report");

		value.addResult().setReference("Observation/1");
		value.addResult().setReference("Observation/2");
		value.addResult().setReference("Observation/3");

		Narrative narrative = new Narrative();
		myNarrativeGenerator.generateNarrative(ourFhirContext, value, narrative);
		String output = narrative.getDiv().getValueAsString();

		ourLog.info(output);
		assertThat(output, StringContains.containsString(value.getCode().getTextElement().getValue()));
	}

	@Test
	@Ignore
	public void testGenerateOperationOutcome() {
		//@formatter:off
		String parse = "<OperationOutcome xmlns=\"http://hl7.org/fhir\">\n" + 
				"   <issue>\n" + 
				"      <severity value=\"error\"/>\n" + 
				"      <diagnostics value=\"ca.uhn.fhir.rest.server.exceptions.InternalErrorException: Failed to call access method&#xa;&#xa;ca.uhn.fhir.rest.server.exceptions.InternalErrorException: Failed to call access method&#xa;&#x9;at ca.uhn.fhir.rest.method.BaseMethodBinding.invokeServerMethod(BaseMethodBinding.java:199)&#xa;&#x9;at ca.uhn.fhir.rest.method.HistoryMethodBinding.invokeServer(HistoryMethodBinding.java:162)&#xa;&#x9;at ca.uhn.fhir.rest.method.BaseResourceReturningMethodBinding.invokeServer(BaseResourceReturningMethodBinding.java:228)&#xa;&#x9;at ca.uhn.fhir.rest.method.HistoryMethodBinding.invokeServer(HistoryMethodBinding.java:1)&#xa;&#x9;at ca.uhn.fhir.rest.server.RestfulServer.handleRequest(RestfulServer.java:534)&#xa;&#x9;at ca.uhn.fhir.rest.server.RestfulServer.doGet(RestfulServer.java:141)&#xa;&#x9;at javax.servlet.http.HttpServlet.service(HttpServlet.java:687)&#xa;&#x9;at javax.servlet.http.HttpServlet.service(HttpServlet.java:790)&#xa;&#x9;at org.apache.catalina.core.StandardWrapper.service(StandardWrapper.java:1682)&#xa;&#x9;at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:344)&#xa;&#x9;at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:214)&#xa;&#x9;at org.ebaysf.web.cors.CORSFilter.handleNonCORS(CORSFilter.java:437)&#xa;&#x9;at org.ebaysf.web.cors.CORSFilter.doFilter(CORSFilter.java:172)&#xa;&#x9;at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:256)&#xa;&#x9;at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:214)&#xa;&#x9;at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:316)&#xa;&#x9;at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:160)&#xa;&#x9;at org.apache.catalina.core.StandardPipeline.doInvoke(StandardPipeline.java:734)&#xa;&#x9;at org.apache.catalina.core.StandardPipeline.invoke(StandardPipeline.java:673)&#xa;&#x9;at com.sun.enterprise.web.WebPipeline.invoke(WebPipeline.java:99)&#xa;&#x9;at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:174)&#xa;&#x9;at org.apache.catalina.connector.CoyoteAdapter.doService(CoyoteAdapter.java:357)&#xa;&#x9;at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:260)&#xa;&#x9;at com.sun.enterprise.v3.services.impl.ContainerMapper.service(ContainerMapper.java:188)&#xa;&#x9;at org.glassfish.grizzly.http.server.HttpHandler.runService(HttpHandler.java:191)&#xa;&#x9;at org.glassfish.grizzly.http.server.HttpHandler.doHandle(HttpHandler.java:168)&#xa;&#x9;at org.glassfish.grizzly.http.server.HttpServerFilter.handleRead(HttpServerFilter.java:189)&#xa;&#x9;at org.glassfish.grizzly.filterchain.ExecutorResolver$9.execute(ExecutorResolver.java:119)&#xa;&#x9;at org.glassfish.grizzly.filterchain.DefaultFilterChain.executeFilter(DefaultFilterChain.java:288)&#xa;&#x9;at org.glassfish.grizzly.filterchain.DefaultFilterChain.executeChainPart(DefaultFilterChain.java:206)&#xa;&#x9;at org.glassfish.grizzly.filterchain.DefaultFilterChain.execute(DefaultFilterChain.java:136)&#xa;&#x9;at org.glassfish.grizzly.filterchain.DefaultFilterChain.process(DefaultFilterChain.java:114)&#xa;&#x9;at org.glassfish.grizzly.ProcessorExecutor.execute(ProcessorExecutor.java:77)&#xa;&#x9;at org.glassfish.grizzly.nio.transport.TCPNIOTransport.fireIOEvent(TCPNIOTransport.java:838)&#xa;&#x9;at org.glassfish.grizzly.strategies.AbstractIOStrategy.fireIOEvent(AbstractIOStrategy.java:113)&#xa;&#x9;at org.glassfish.grizzly.strategies.WorkerThreadIOStrategy.run0(WorkerThreadIOStrategy.java:115)&#xa;&#x9;at org.glassfish.grizzly.strategies.WorkerThreadIOStrategy.access$100(WorkerThreadIOStrategy.java:55)&#xa;&#x9;at org.glassfish.grizzly.strategies.WorkerThreadIOStrategy$WorkerThreadRunnable.run(WorkerThreadIOStrategy.java:135)&#xa;&#x9;at org.glassfish.grizzly.threadpool.AbstractThreadPool$Worker.doWork(AbstractThreadPool.java:564)&#xa;&#x9;at org.glassfish.grizzly.threadpool.AbstractThreadPool$Worker.run(AbstractThreadPool.java:544)&#xa;&#x9;at java.lang.Thread.run(Thread.java:722)&#xa;Caused by: java.lang.reflect.InvocationTargetException&#xa;&#x9;at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)&#xa;&#x9;at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)&#xa;&#x9;at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)&#xa;&#x9;at java.lang.reflect.Method.invoke(Method.java:601)&#xa;&#x9;at ca.uhn.fhir.rest.method.BaseMethodBinding.invokeServerMethod(BaseMethodBinding.java:194)&#xa;&#x9;... 40 more&#xa;Caused by: java.lang.NumberFormatException: For input string: &quot;3cb9a027-9e02-488d-8d01-952553d6be4e&quot;&#xa;&#x9;at java.lang.NumberFormatException.forInputString(NumberFormatException.java:65)&#xa;&#x9;at java.lang.Long.parseLong(Long.java:441)&#xa;&#x9;at java.lang.Long.parseLong(Long.java:483)&#xa;&#x9;at ca.uhn.fhir.model.primitive.IdDt.getIdPartAsLong(IdDt.java:194)&#xa;&#x9;at ca.uhn.fhir.jpa.provider.JpaResourceProvider.getHistoryForResourceInstance(JpaResourceProvider.java:81)&#xa;&#x9;... 45 more&#xa;\"/>\n" + 
				"   </issue>\n" + 
				"   <issue>\n" + 
				"      <severity value=\"warning\"/>\n" + 
				"      <diagnostics value=\"YThis is a warning\"/>\n" + 
				"   </issue>\n" + 
				"</OperationOutcome>";
		//@formatter:on

		OperationOutcome oo = ourFhirContext.newXmlParser().parseResource(OperationOutcome.class, parse);

		Narrative narrative = new Narrative();
		myNarrativeGenerator.generateNarrative(ourFhirContext, oo, narrative);
		String output = narrative.getDiv().getValueAsString();

		ourLog.info(output);

		assertThat(output, containsString("<td><pre>YThis is a warning</pre></td>"));
	}


	@Test
	@Ignore
	public void testGenerateDiagnosticReportWithObservations() throws DataFormatException {
		DiagnosticReport value = new DiagnosticReport();

		value.getIssuedElement().setValueAsString("2011-02-22T11:13:00");
		value.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

		value.getCode().setText("Some & Diagnostic Report");
		{
			Observation obs = new Observation();
			obs.getCode().addCoding().setCode("1938HB").setDisplay("Hemoglobin");
			obs.setValue(new Quantity(null, 2.223, null, null, "mg/L"));
			obs.addReferenceRange().setLow((SimpleQuantity) new SimpleQuantity().setValue(2.20)).setHigh((SimpleQuantity) new SimpleQuantity().setValue(2.99));
			obs.setStatus(Observation.ObservationStatus.FINAL);
			obs.addNote().setText("This is a result comment");
			Reference result = value.addResult();
			result.setResource(obs);
		}
		{
			Observation obs = new Observation();
			obs.setValue(new StringType("HELLO!"));
			value.addResult().setResource(obs);
		}
		{
			Observation obs = new Observation();
			obs.setCode(new CodeableConcept().addCoding(new Coding("AA", "BB", null)));
			value.addResult().setResource(obs);
		}

		Narrative narrative = new Narrative();
		myNarrativeGenerator.generateNarrative(ourFhirContext, value, narrative);
		String output = narrative.getDiv().getValueAsString();

		ourLog.info(output);
		assertThat(output, StringContains.containsString("<div class=\"hapiHeaderText\"> Some &amp; Diagnostic Report </div>"));

	}

	@Test
	@Ignore
	public void testGenerateMedicationPrescription() {
		MedicationRequest mp = new MedicationRequest();
		mp.setId("12345");
		Medication med = new Medication();
		med.getCode().setText("ciproflaxin");
		Reference medRef = new Reference(med);
		mp.setMedication(medRef);
		mp.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
		mp.setAuthoredOnElement(new DateTimeType("2014-09-01"));

		Narrative narrative = new Narrative();
		myNarrativeGenerator.generateNarrative(ourFhirContext, mp, narrative);

		assertTrue("Expected medication name of ciprofloaxin within narrative: " + narrative.getDiv().toString(), narrative.getDiv().toString().indexOf("ciprofloaxin") > -1);
		assertTrue("Expected string status of ACTIVE within narrative: " + narrative.getDiv().toString(), narrative.getDiv().toString().indexOf("ACTIVE") > -1);

	}

	@Test
	public void testGenerateMedication() {
		Medication med = new Medication();
		med.getCode().setText("ciproflaxin");

		myNarrativeGenerator.populateResourceNarrative(ourFhirContext, med);

		String string = med.getText().getDiv().getValueAsString();
		assertThat(string, containsString("ciproflaxin"));
	}
}