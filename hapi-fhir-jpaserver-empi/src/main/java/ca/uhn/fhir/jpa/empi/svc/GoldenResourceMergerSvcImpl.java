package ca.uhn.fhir.jpa.empi.svc;

/*-
 * #%L
 * HAPI FHIR JPA Server - Enterprise Master Patient Index
 * %%
 * Copyright (C) 2014 - 2020 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.empi.api.EmpiLinkSourceEnum;
import ca.uhn.fhir.empi.api.EmpiMatchResultEnum;
import ca.uhn.fhir.empi.api.IEmpiLinkSvc;
import ca.uhn.fhir.empi.api.IGoldenResourceMergerSvc;
import ca.uhn.fhir.empi.log.Logs;
import ca.uhn.fhir.empi.model.MdmTransactionContext;
import ca.uhn.fhir.empi.util.PersonHelper;
import ca.uhn.fhir.jpa.dao.index.IdHelperService;
import ca.uhn.fhir.jpa.empi.dao.EmpiLinkDaoSvc;
import ca.uhn.fhir.jpa.entity.EmpiLink;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GoldenResourceMergerSvcImpl implements IGoldenResourceMergerSvc {
	private static final Logger ourLog = Logs.getEmpiTroubleshootingLog();

	@Autowired
	PersonHelper myPersonHelper;
	@Autowired
	EmpiLinkDaoSvc myEmpiLinkDaoSvc;
	@Autowired
	IEmpiLinkSvc myEmpiLinkSvc;
	@Autowired
	IdHelperService myIdHelperService;
	@Autowired
	EmpiResourceDaoSvc myEmpiResourceDaoSvc;

	@Override
	@Transactional
	public IAnyResource mergeGoldenResources(IAnyResource theFromGoldenResource, IAnyResource theToGoldenResource, MdmTransactionContext theMdmTransactionContext) {
		Long toGoldenResourcePid = myIdHelperService.getPidOrThrowException(theToGoldenResource);

		myPersonHelper.mergeFields(theFromGoldenResource, theToGoldenResource);

		mergeGoldenResourceLinks(theFromGoldenResource, theToGoldenResource, toGoldenResourcePid, theMdmTransactionContext);

		//TODO GGG MDM: Is this just cleanup? In theory, the merge step from before shoulda done this..
		removeTargetLinks(theFromGoldenResource, theToGoldenResource, theMdmTransactionContext);


		myEmpiResourceDaoSvc.upsertSourceResource(theFromGoldenResource, theMdmTransactionContext.getResourceType());

		Long fromGoldenResourcePid = myIdHelperService.getPidOrThrowException(theFromGoldenResource);
		addMergeLink(toGoldenResourcePid, fromGoldenResourcePid, theMdmTransactionContext.getResourceType());
		//myPersonHelper.deactivateResource(theFromGoldenResource);

		//Remove HAPI-EMPI Managed TAG, add a different Ttag? e.g. HAPI-EMPI-DEPRECATED or something? This would serve the purpose.

		myEmpiResourceDaoSvc.upsertSourceResource(theFromGoldenResource, theMdmTransactionContext.getResourceType());

		log(theMdmTransactionContext, "Merged " + theFromGoldenResource.getIdElement().toVersionless() + " into " + theToGoldenResource.getIdElement().toVersionless());
		return theToGoldenResource;
	}

	/**
	 * Removes non-manual links from source to target
	 *
	 * @param theFrom                   Target of the link
	 * @param theTo                     Source resource of the link
	 * @param theMdmTransactionContext Context to keep track of the deletions
	 */
	private void removeTargetLinks(IAnyResource theFrom, IAnyResource theTo, MdmTransactionContext theMdmTransactionContext) {
		List<EmpiLink> allLinksWithTheFromAsTarget = myEmpiLinkDaoSvc.findEmpiLinksByGoldenResource(theFrom);
		allLinksWithTheFromAsTarget
			.stream()
			//TODO GGG NG MDM: Why are we keeping manual links? Haven't we already copied those over in the previous merge step?
			.filter(EmpiLink::isAuto) // only keep manual links
			.forEach(l -> {
				theMdmTransactionContext.addTransactionLogMessage(String.format("Deleting link %s", l));
				myEmpiLinkDaoSvc.deleteLink(l);
			});
	}

	private void addMergeLink(Long theSourceResourcePidAkaActive, Long theTargetResourcePidAkaDeactivated, String theResourceType) {
		EmpiLink empiLink = myEmpiLinkDaoSvc
			.getOrCreateEmpiLinkBySourceResourcePidAndTargetResourcePid(theSourceResourcePidAkaActive, theTargetResourcePidAkaDeactivated);

		empiLink
			.setEmpiTargetType(theResourceType)
			.setMatchResult(EmpiMatchResultEnum.REDIRECT)
			.setLinkSource(EmpiLinkSourceEnum.MANUAL);
		myEmpiLinkDaoSvc.save(empiLink);
	}


	private void mergeGoldenResourceLinks(IAnyResource theFromResource, IAnyResource theToResource, Long theToResourcePid, MdmTransactionContext theMdmTransactionContext) {
		List<EmpiLink> fromLinks = myEmpiLinkDaoSvc.findEmpiLinksByGoldenResource(theFromResource); // fromLinks - links going to theFromResource
		List<EmpiLink> toLinks = myEmpiLinkDaoSvc.findEmpiLinksByGoldenResource(theToResource); // toLinks - links going to theToResource

		// For each incomingLink, either ignore it, move it, or replace the original one
		for (EmpiLink fromLink : fromLinks) {
			Optional<EmpiLink> optionalToLink = findFirstLinkWithMatchingTarget(toLinks, fromLink);
			if (optionalToLink.isPresent()) {
				// toLinks.remove(optionalToLink);

				// The original links already contain this target, so move it over to the toResource
				EmpiLink toLink = optionalToLink.get();
				if (fromLink.isManual()) {
					switch (toLink.getLinkSource()) {
						case AUTO:
							ourLog.trace("MANUAL overrides AUT0.  Deleting link {}", toLink);
							myEmpiLinkDaoSvc.deleteLink(toLink);
							break;
						case MANUAL:
							if (fromLink.getMatchResult() != toLink.getMatchResult()) {
								throw new InvalidRequestException("A MANUAL " + fromLink.getMatchResult() + " link may not be merged into a MANUAL " + toLink.getMatchResult() + " link for the same target");
							}
					}
				} else {
					// Ignore the case where the incoming link is AUTO
					continue;
				}
			}
			// The original links didn't contain this target, so move it over to the toPerson
			fromLink.setGoldenResourcePid(theToResourcePid);
			ourLog.trace("Saving link {}", fromLink);
			myEmpiLinkDaoSvc.save(fromLink);
		}
	}

	private Optional<EmpiLink> findFirstLinkWithMatchingTarget(List<EmpiLink> theEmpiLinks, EmpiLink theLinkWithTargetToMatch) {
		return theEmpiLinks.stream()
			.filter(empiLink -> empiLink.getTargetPid().equals(theLinkWithTargetToMatch.getTargetPid()))
			.findFirst();
	}

	private void log(MdmTransactionContext theMdmTransactionContext, String theMessage) {
		theMdmTransactionContext.addTransactionLogMessage(theMessage);
		ourLog.debug(theMessage);
	}
}