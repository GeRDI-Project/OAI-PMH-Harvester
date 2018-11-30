/**
 * Copyright © 2018 Tobias Weber (http://www.gerdi-project.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gerdiproject.harvest.oaipmh.strategies.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gerdiproject.harvest.IDocument;
import de.gerdiproject.harvest.oaipmh.constants.Iso19139StrategyConstants;
import de.gerdiproject.harvest.oaipmh.strategies.IStrategy;
import de.gerdiproject.json.datacite.Creator;
import de.gerdiproject.json.datacite.DataCiteJson;
import de.gerdiproject.json.datacite.Date;
import de.gerdiproject.json.datacite.Description;
import de.gerdiproject.json.datacite.GeoLocation;
import de.gerdiproject.json.datacite.Identifier;
import de.gerdiproject.json.datacite.ResourceType;
import de.gerdiproject.json.datacite.Title;
import de.gerdiproject.json.datacite.abstr.AbstractDate;
import de.gerdiproject.json.datacite.enums.DateType;
import de.gerdiproject.json.datacite.enums.DescriptionType;
import de.gerdiproject.json.datacite.enums.ResourceTypeGeneral;
import de.gerdiproject.json.datacite.extension.ResearchData;
import de.gerdiproject.json.geo.GeoJson;
import de.gerdiproject.json.geo.Point;

/**
 * A harvesting strategy for the ISO 19139 metadata standard.<br>
 *  https://www.iso.org/standard/32557.html
 *
 * @author Tobias Weber
 *
 */
public class OaiPmhIso19139Strategy implements IStrategy
{
    protected static final Logger logger
        = LoggerFactory.getLogger(OaiPmhIso19139Strategy.class.getName());

    @Override
    public IDocument harvestRecord(Element record)
    {
        Boolean isRecordDeleted = record.children()
                                  .first()
                                  .attr(Iso19139StrategyConstants.RECORD_STATUS)
                                  .equals(Iso19139StrategyConstants.RECORD_STATUS_DEL);

        // check if entry/record is "deleted" from repository
        if (isRecordDeleted)
            return null;

        /*
         ********************************************************************************
         * Start for mandatory fields (may not fail)
         ********************************************************************************
         */
        /*
         * E2 RepositoryIdentifier
         * Cannot be determined via Metadata
         * TODO A constant is not a good idea, should be configurable
         */
        final String repositoryIdentifier = Iso19139StrategyConstants.REPOSITORY_IDENTIFIER;

        //prepare for the other metadata
        final DataCiteJson document = new DataCiteJson(repositoryIdentifier);
        document.setRepositoryIdentifier(repositoryIdentifier);
        Element metadata = record.select(Iso19139StrategyConstants.RECORD_METADATA).first();

        Identifier identifier = parseIdentifier(metadata);

        if (identifier != null)
            document.setIdentifier(identifier);

        /*
         * D2 Creator
         * This field is not easily mappable from ISO19139 to DataCite.
         * We will use the same value as for Publisher.
         */
        List<Creator> creatorList = new LinkedList<>();
        final Element creator = metadata.select(Iso19139StrategyConstants.PUBLISHER).first();

        if (creator != null) {
            creatorList.add(new Creator(creator.text()));
            document.setCreators(creatorList);
        }

        /*
         * D3 Title
         */
        List<Title> titleList = new LinkedList<>();
        final Element title = metadata.select(Iso19139StrategyConstants.TITLE).first();

        if (title != null) {
            titleList.add(new Title(title.text()));
            document.setTitles(titleList);
        }

        /*
         * D4 Publisher
         */
        final Element publisher = metadata.select(Iso19139StrategyConstants.PUBLISHER).first();

        if (publisher != null)
            document.setPublisher(publisher.text());

        /*
         * D5 PublicationYear
         * This field is not easily mappable from ISO19139 to DataCite.
         * We will use the datestamp of the metadata record, since it is the best approximation:
         * "The date which specifies when the metadata record was created or updated."
         * This could be overwritten after we parsed the dates (but these might be missing)
         */
        final Element datestamp = metadata.select(Iso19139StrategyConstants.DATESTAMP).first();

        if (datestamp != null) {
            Calendar cal = null;

            try {
                cal = DatatypeConverter.parseDateTime(datestamp.text());
            } catch (IllegalArgumentException e) {
                logger.warn("Datestamp does not seem to be a date: {}",
                            metadata.select(Iso19139StrategyConstants.DATESTAMP).text());
            }

            if (cal != null)
                document.setPublicationYear((short) cal.get(Calendar.YEAR));
        }

        /*
         * D10 ResourceType
         */
        final Element resourceType
            = metadata.select(Iso19139StrategyConstants.RESOURCE_TYPE).first();

        if (resourceType != null) {
            document.setResourceType(new ResourceType(
                                         resourceType.text(),
                                         ResourceTypeGeneral.Dataset));
        }

        /*
         * E3 ResearchData
         * Since we do not have a DOI, we will return null, if there is no valid URL for
         * the research data available.
         */
        List<ResearchData> researchDataList = new LinkedList<>();
        final Element researchDataURLString
            = metadata.select(Iso19139StrategyConstants.RESEARCH_DATA).first();

        if (researchDataURLString != null) {
            //Check whether URL is - wait for it - valid
            try {
                new URL(researchDataURLString.text());

                if (!titleList.isEmpty()) {
                    ResearchData researchData = new ResearchData(
                        researchDataURLString.text(),
                        titleList.get(0).getValue());
                    researchDataList.add(researchData);
                    document.setResearchDataList(researchDataList);
                }
            } catch (MalformedURLException e) {
                logger.warn("URL {} is not valid, skipping", researchDataURLString);
            }
        }

        /*
         ********************************************************************************
         * Start for non-mandatory fields (may fail)
         ********************************************************************************
         */

        /*
         * The following DataCite and GeRDI generic extension fields are currently
         * NOT implemented:
         * - D7     Contributor
         * - D9     Language
         * - D11    AlternateIdentifier
         * - D12    RelatedIdentifier
         * - D13    Size
         * - D14    Format
         * - D15    Version
         * - D16    Rights
         * - D19    FundingReference
         * - E1     WebLink
         * - E4     ResearchDiscipline
         * For a possible mapping from ISO19139 to these fields
         * see https://wiki.gerdi-project.de/x/8YDmAQ
         */

        /*
         * D8 Date
         */
        List<AbstractDate> dateList = parseDates(metadata);

        if (!dateList.isEmpty()) {
            document.setDates(dateList);

            for (AbstractDate date : dateList) {
                if (date.getType() == DateType.Issued) {
                    Calendar cal = DatatypeConverter.parseDateTime(date.getValue());
                    document.setPublicationYear((short) cal.get(Calendar.YEAR));
                    break;
                }
            }
        }

        /*
         * D17 Description
         */
        List<Description> descriptionList = new LinkedList<>();
        final Element description = metadata.select(Iso19139StrategyConstants.DESCRIPTIONS).first();

        if (description != null) {
            descriptionList.add(new Description(description.text(), DescriptionType.Abstract));
            document.setDescriptions(descriptionList);
        }

        /*
         * D18 GeoLocation
         */
        List<GeoLocation> geoLocationList = parseGeoLocations(metadata);

        if (! geoLocationList.isEmpty())
            document.setGeoLocations(geoLocationList);

        return document;
    }

    /**
     * Parses metadata for an identifier (D1) in an ISO19139 metadata record
     *
     * @param metadata metadata to be parsed
     * @todo ISO19139 does not guarantee a DOI, but a "unique and persistent identifier",
     *       up to now these are URNs - the following call will set the identifierType to DOI
     *       nevertheless
     *
     * @return Identifier The String representation of the identifier
     */
    private Identifier parseIdentifier(Element metadata)
    {
        final Element identifier = metadata.select(Iso19139StrategyConstants.IDENTIFIER).first();
        return new Identifier(identifier.text());
    }

    /**
     * Parses metadata for dates (D8) in an ISO19139 metadata record that are mappable
     * to a DataCite field
     *
     * @param metadata metadata to be parsed
     *
     * @return List<AbstractDate> List of date-data that are mappable to a DataCite field
     *                            (Created, Issued, Updated)
     */
    private List<AbstractDate> parseDates(Element metadata)
    {
        List<AbstractDate> dateList = new LinkedList<>();
        Elements isoDates = metadata.select(Iso19139StrategyConstants.DATES);

        for (Element isoDate : isoDates) {
            final DateType dateType = Iso19139StrategyConstants.DATE_TYPE_MAP.get(
                                          isoDate.select(Iso19139StrategyConstants.DATE_TYPE).text());

            if (dateType != null) {
                dateList.add(
                    new Date(isoDate.select(Iso19139StrategyConstants.DATE).text(), dateType));
            }
        }

        return dateList;
    }

    /**
     * Parses metadata for geolocations (D18) in an ISO19139 metadata record
     *
     * @param metadata metadata to be parsed
     *
     * @return List<GeoLocation> List of geolocations which are given as a bounding box
     */
    private List<GeoLocation> parseGeoLocations(Element metadata)
    {
        List<GeoLocation> geoLocationList = new LinkedList<>();
        Elements isoGeoLocations = metadata.select(Iso19139StrategyConstants.GEOLOCS);

        for (Element isoGeoLocation : isoGeoLocations) {
            GeoLocation geoLocation = new GeoLocation();
            double west, east, south, north;

            try {
                west = Double.parseDouble(
                           isoGeoLocation.select(Iso19139StrategyConstants.GEOLOCS_WEST).text());
                east = Double.parseDouble(
                           isoGeoLocation.select(Iso19139StrategyConstants.GEOLOCS_EAST).text());
                south = Double.parseDouble(
                            isoGeoLocation.select(Iso19139StrategyConstants.GEOLOCS_SOUTH).text());
                north = Double.parseDouble(
                            isoGeoLocation.select(Iso19139StrategyConstants.GEOLOCS_NORTH).text());
            } catch (NullPointerException | NumberFormatException e) {
                logger.info("Ignoring geolocation {} for document {}, has no valid coordinations",
                            parseIdentifier(metadata).getValue(),
                            isoGeoLocation.text());
                continue;
            }

            //is it a point or a polygon?
            if (west == east && south == north)
                geoLocation.setPoint(new GeoJson(new Point(west, south)));
            else
                geoLocation.setBox(west, east, south, north);

            geoLocationList.add(geoLocation);
        }

        return geoLocationList;
    }
}