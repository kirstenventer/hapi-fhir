package ca.uhn.fhir.rest.server;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu.composite.CodingDt;
import ca.uhn.fhir.model.dstu.resource.Observation;
import ca.uhn.fhir.model.dstu.resource.Patient;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.testutil.RandomServerPortProvider;

/**
 * Created by dsotnikov on 2/25/2014.
 */
public class SearchTest {

	private static CloseableHttpClient ourClient;
	private static FhirContext ourCtx = new FhirContext();
	private static int ourPort;
	private static Server ourServer;

	@Test
	public void testOmitEmptyOptionalParam() throws Exception {
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?_id=");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);
		assertEquals(1, bundle.getEntries().size());

		Patient p = bundle.getResources(Patient.class).get(0);
		assertEquals(null, p.getNameFirstRep().getFamilyFirstRep().getValue());
	}

	@Test
	public void testSearchById() throws Exception {
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?_id=aaa");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);
		assertEquals(1, bundle.getEntries().size());

		Patient p = bundle.getResources(Patient.class).get(0);
		assertEquals("idaaa", p.getNameFirstRep().getFamilyAsSingleString());
		assertEquals("IDAAA (identifier123)", bundle.getEntries().get(0).getTitle().getValue());
	}
	
	
	@Test
	public void testSearchByPost() throws Exception {
		HttpPost filePost = new HttpPost("http://localhost:" + ourPort + "/Patient/_search");

		// add parameters to the post method  
        List <NameValuePair> parameters = new ArrayList <NameValuePair>();   
        parameters.add(new BasicNameValuePair("_id", "aaa"));   
          
        UrlEncodedFormEntity sendentity = new UrlEncodedFormEntity(parameters, "UTF-8");  
        filePost.setEntity(sendentity);   
		
		HttpResponse status = ourClient.execute(filePost);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);
		assertEquals(1, bundle.getEntries().size());

		Patient p = bundle.getResources(Patient.class).get(0);
		assertEquals("idaaa", p.getNameFirstRep().getFamilyAsSingleString());
		assertEquals("IDAAA (identifier123)", bundle.getEntries().get(0).getTitle().getValue());
	}

	@Test
	public void testSearchGetWithUnderscoreSearch() throws Exception {
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort+"/Observation/_search?subject%3APatient=100&name=3141-9%2C8302-2%2C8287-5%2C39156-5");
		
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);
		assertEquals(1, bundle.getEntries().size());

		Observation p = bundle.getResources(Observation.class).get(0);
		assertEquals("Patient/100", p.getSubject().getReference().toString());
		assertEquals(4, p.getName().getCoding().size());
		assertEquals("3141-9", p.getName().getCoding().get(0).getCode().getValue());
		assertEquals("8302-2", p.getName().getCoding().get(1).getCode().getValue());
		
	}

	@AfterClass
	public static void afterClass() throws Exception {
		ourServer.stop();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		ourPort = RandomServerPortProvider.findFreePort();
		ourServer = new Server(ourPort);

		DummyPatientResourceProvider patientProvider = new DummyPatientResourceProvider();

		ServletHandler proxyHandler = new ServletHandler();
		RestfulServer servlet = new RestfulServer();
		servlet.getFhirContext().setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());

		servlet.setResourceProviders(patientProvider, new DummyObservationResourceProvider());
		ServletHolder servletHolder = new ServletHolder(servlet);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();

	}

	public static class DummyObservationResourceProvider implements IResourceProvider{

		@Override
		public Class<? extends IResource> getResourceType() {
			return Observation.class;
		}
		
		@Search
		public Observation search(@RequiredParam(name="subject") ReferenceParam theSubject, @RequiredParam(name="name") TokenOrListParam theName) {
			Observation o = new Observation();
			o.setId("1");

			o.getSubject().setReference(theSubject.getResourceType() + "/" + theSubject.getIdPart());
			for (CodingDt next : theName.getListAsCodings()) {
				o.getName().getCoding().add(next);
			}
			
			return o;
		}
		
	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class DummyPatientResourceProvider implements IResourceProvider {
		
		@Search
		public List<Patient> findPatient(@OptionalParam(name = "_id") StringParam theParam) {
			ArrayList<Patient> retVal = new ArrayList<Patient>();

			Patient patient = new Patient();
			patient.setId("1");
			patient.addIdentifier("system", "identifier123");
			if (theParam != null) {
				patient.addName().addFamily("id" + theParam.getValue());
			}
			retVal.add(patient);
			return retVal;
		}

		@Override
		public Class<? extends IResource> getResourceType() {
			return Patient.class;
		}

	}

}