package ca.uhn.fhir.cql.dstu3.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.cql.common.provider.LibraryResolutionProvider;
import ca.uhn.fhir.cql.common.provider.LibrarySourceProvider;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriParam;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.hl7.fhir.dstu3.model.Attachment;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Library;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.tooling.library.stu3.NarrativeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class LibraryResolutionProviderImpl implements LibraryResolutionProvider<Library> {
	@Autowired
	private FhirContext myFhirContext;
	@Autowired
	private IFhirResourceDao<Library> myLibraryDao;
	@Autowired
	private NarrativeProvider narrativeProvider;

	private LibrarySourceProvider<Library, Attachment> librarySourceProvider;

	private LibrarySourceProvider<Library, Attachment> getLibrarySourceProvider() {
		if (librarySourceProvider == null) {
			librarySourceProvider = new LibrarySourceProvider<Library, Attachment>(this.getLibraryResolutionProvider(),
				x -> x.getContent(), x -> x.getContentType(), x -> x.getData());
		}
		return librarySourceProvider;
	}

	private LibraryResolutionProvider<Library> getLibraryResolutionProvider() {
		return this;
	}

	// TODO: Figure out if we should throw an exception or something here.
	@Override
	public void update(Library library) {
		myLibraryDao.update(library);
	}

	@Override
	public Library resolveLibraryById(String libraryId) {
		try {
			return myLibraryDao.read(new IdType(libraryId));
		} catch (Exception e) {
			throw new IllegalArgumentException(String.format("Could not resolve library id %s", libraryId));
		}
	}

	@Override
	public Library resolveLibraryByCanonicalUrl(String url) {
		Objects.requireNonNull(url, "url must not be null");

		String[] parts = url.split("\\|");
		String resourceUrl = parts[0];
		String version = null;
		if (parts.length > 1) {
			version = parts[1];
		}

		SearchParameterMap map = new SearchParameterMap();
		map.add("url", new UriParam(resourceUrl));
		if (version != null) {
			map.add("version", new TokenParam(version));
		}

		ca.uhn.fhir.rest.api.server.IBundleProvider bundleProvider = myLibraryDao.search(map);

		if (bundleProvider.size() == 0) {
			return null;
		}
		List<IBaseResource> resourceList = bundleProvider.getResources(0, bundleProvider.size());
		return LibraryResolutionProvider.selectFromList(resolveLibraries(resourceList), version, x -> x.getVersion());
	}

	@Override
	public Library resolveLibraryByName(String libraryName, String libraryVersion) {
		Iterable<org.hl7.fhir.dstu3.model.Library> libraries = getLibrariesByName(libraryName);
		org.hl7.fhir.dstu3.model.Library library = LibraryResolutionProvider.selectFromList(libraries, libraryVersion,
			x -> x.getVersion());

		if (library == null) {
			throw new IllegalArgumentException(String.format("Could not resolve library name %s", libraryName));
		}

		return library;
	}

	private Iterable<org.hl7.fhir.dstu3.model.Library> getLibrariesByName(String name) {
		// Search for libraries by name
		SearchParameterMap map = new SearchParameterMap();
		map.add("name", new StringParam(name, true));
		ca.uhn.fhir.rest.api.server.IBundleProvider bundleProvider = myLibraryDao.search(map);

		if (bundleProvider.size() == 0) {
			return new ArrayList<>();
		}
		List<IBaseResource> resourceList = bundleProvider.getResources(0, bundleProvider.size());
		return resolveLibraries(resourceList);
	}

	private Iterable<org.hl7.fhir.dstu3.model.Library> resolveLibraries(List<IBaseResource> resourceList) {
		List<org.hl7.fhir.dstu3.model.Library> ret = new ArrayList<>();
		for (IBaseResource res : resourceList) {
			Class<?> clazz = res.getClass();
			ret.add((org.hl7.fhir.dstu3.model.Library) clazz.cast(res));
		}
		return ret;
	}
}