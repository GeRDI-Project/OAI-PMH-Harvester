/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package de.gerdiproject.harvest.oaipmh.strategies.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import de.gerdiproject.harvest.IDocument;
import de.gerdiproject.harvest.oaipmh.strategies.IStrategy;
import de.gerdiproject.json.datacite.Contributor;
import de.gerdiproject.json.datacite.Creator;
import de.gerdiproject.json.datacite.DataCiteJson;
import de.gerdiproject.json.datacite.Date;
import de.gerdiproject.json.datacite.Description;
import de.gerdiproject.json.datacite.Identifier;
import de.gerdiproject.json.datacite.RelatedIdentifier;
import de.gerdiproject.json.datacite.Subject;
import de.gerdiproject.json.datacite.Title;
import de.gerdiproject.json.datacite.abstr.AbstractDate;
import de.gerdiproject.json.datacite.enums.ContributorType;
import de.gerdiproject.json.datacite.enums.DateType;
import de.gerdiproject.json.datacite.enums.DescriptionType;
import de.gerdiproject.json.datacite.enums.RelatedIdentifierType;
import de.gerdiproject.json.datacite.enums.RelationType;
import de.gerdiproject.json.datacite.extension.WebLink;
import de.gerdiproject.json.datacite.extension.enums.WebLinkType;

/**
 * TODO
 * @author Jan Frömberg
 */
public class OaiPmhDublinCoreStrategy implements IStrategy
{
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy'-'MM'-'dd");

    @Override
    public IDocument harvestRecord(Element record)
    {
        //each entry-node starts with record subelements are header and metadata
        //Example: https://www.cancerdata.org/oai?verb=ListRecords&from=2017-11-01&metadataPrefix=oai_dc
        DataCiteJson document = new DataCiteJson();
        // get header and meta data stuff for each record
        Elements children = record.children();
        Elements headers = children.select("header");
        Boolean deleted = children.first().attr("status").equals("deleted") ? true : false;
        //logger.info("Identifier deleted?: " + deleted.toString() + " (" + children.first().attr("status") + ")");
        Elements metadata = children.select("metadata");

        List<WebLink> links = new LinkedList<>();
        List<RelatedIdentifier> relatedIdentifiers = new LinkedList<>();
        List<AbstractDate> dates = new LinkedList<>();
        List<Title> titles = new LinkedList<>();
        List<Description> descriptions = new LinkedList<>();
        List<Subject> subjects = new LinkedList<>();
        //List<ResourceType> rtypes = new LinkedList<>();
        List<Creator> creators = new LinkedList<>();
        List<Contributor> contributors = new LinkedList<>();
        List<String> dctype = new LinkedList<>();

        // set document overhead
        //Attributes attributes = entry.attributes();
        //String version = attributes.get("version");
        //document.setVersion(version);
        //document.setResourceType(RESOURCE_TYPE);
        // TODO document.setPublisher(PROVIDER);
        //document.setFormats(FORMATS);

        // get identifier and datestamp
        Element identifier = headers.select("identifier").first();
        //String identifier_handle = identifier.text().split(":")[2];
        //logger.info("Identifier Handle (Header): " + identifier_handle);
        Identifier mainIdentifier = new Identifier(identifier.text());

        // get source
        //Source source = new Source(String.format(VIEW_URL, identifier_handle), PROVIDER);
        //source.setProviderURI(PROVIDER_URL);
        //document.setSources(source);

        // get last updated
        String recorddate = headers.select("datestamp").first().text();
        Date updatedDate = new Date(recorddate, DateType.Updated);
        dates.add(updatedDate);

        //check if Entry is "deleted"
        if (deleted) {
            document.setVersion("deleted");
            document.setIdentifier(mainIdentifier);

            // add dates if there are any
            if (!dates.isEmpty())
                document.setDates(dates);

            return document;
        }


        // get publication date
        Calendar cal = Calendar.getInstance();
        Elements pubdate = metadata.select("dc|date");

        try {
            cal.setTime(dateFormat.parse(pubdate.first().text()));
            document.setPublicationYear((short) cal.get(Calendar.YEAR));

            Date publicationDate = new Date(pubdate.first().text(), DateType.Available);
            dates.add(publicationDate);
        } catch (ParseException e) { //NOPMD do nothing. just do not add the date if it does not exist
        }

        // get resource types
        Elements dctypes = metadata.select("dc|type");

        for (Element e : dctypes)
            dctype.add(e.text());

        document.setFormats(dctype);

        // get creators
        Elements creatorElements = metadata.select("dc|creator");

        for (Element e : creatorElements) {
            Creator creator = new Creator(e.text());
            creators.add(creator);
        }

        document.setCreators(creators);

        // get contributors
        Elements contribElements = metadata.select("dc|contributor");

        for (Element e : contribElements) {
            Contributor contrib = new Contributor(e.text(), ContributorType.ContactPerson);
            contributors.add(contrib);
        }

        document.setContributors(contributors);

        // get titles
        Elements dctitles = metadata.select("dc|title");

        for (Element title : dctitles) {
            Title dctitle = new Title(title.text());
            titles.add(dctitle);
        }

        document.setTitles(titles);

        // get descriptions
        Elements descriptionElements = metadata.select("dc|description");

        for (Element descElement : descriptionElements) {
            Description description = new Description(descElement.text(), DescriptionType.Abstract);
            descriptions.add(description);
        }

        document.setDescriptions(descriptions);

        // get web links
        // TODO WebLink logoLink = new WebLink(LOGO_URL);
        //logoLink.setName("Logo");
        //logoLink.setType(WebLinkType.ProviderLogoURL);
        //links.add(logoLink);

        // get identifier URLs
        Elements identEles = metadata.select("dc|identifier");
        int numidents = identEles.size();

        for (Element identElement : identEles) {
            WebLink viewLink = new WebLink(identElement.text());
            viewLink.setName("Identifier" + numidents);
            viewLink.setType(WebLinkType.ViewURL);
            links.add(viewLink);
            numidents--;
        }

        // get keyword subjects
        Elements dcsubjects = metadata.select("dc|subject");

        for (Element subject : dcsubjects) {
            Subject dcsubject = new Subject(subject.text());
            subjects.add(dcsubject);
        }

        document.setSubjects(subjects);

        // parse references
        Elements referenceElements = metadata.select("DOI");

        for (Element doiRef : referenceElements) {
            relatedIdentifiers.add(new RelatedIdentifier(
                                       doiRef.text(),
                                       RelatedIdentifierType.DOI,
                                       RelationType.IsReferencedBy));
        }

        // compile a document
        document.setIdentifier(mainIdentifier);
        document.setWebLinks(links);

        // add dates if there are any
        if (!dates.isEmpty())
            document.setDates(dates);

        // add related identifiers if there are any
        if (!relatedIdentifiers.isEmpty())
            document.setRelatedIdentifiers(relatedIdentifiers);

        return document;
    }


    /*private static ResourceType createResourceType() //TODO: how to deal with that and other meta data formats like ore, mets, etc
    {
        ResourceType resourceType = new ResourceType("Whatever Data", ResourceTypeGeneral.Dataset);

        return resourceType;
    }*/
}
