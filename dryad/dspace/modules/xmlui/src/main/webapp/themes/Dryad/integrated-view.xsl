<?xml version="1.0" encoding="UTF-8"?>

<!-- This stylesheet controls the view/display of the item pages (package
	and file). -->

<!-- If you use an XML editor to reformat this page make sure that the i18n:text
	elements do not break across separate lines; the text will fail to be internationalized
	if this happens and the i18n text is what will be displayed on the Web page. -->

<xsl:stylesheet xmlns="http://www.w3.org/1999/xhtml"
                xmlns:i18n="http://apache.org/cocoon/i18n/2.1" xmlns:dri="http://di.tamu.edu/DRI/1.0/"
                xmlns:mets="http://www.loc.gov/METS/" xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:dim="http://www.dspace.org/xmlns/dspace/dim" xmlns:mods="http://www.loc.gov/mods/v3"
                xmlns:xlink="http://www.w3.org/TR/xlink/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xalan="http://xml.apache.org/xalan" xmlns:datetime="http://exslt.org/dates-and-times"
                xmlns:encoder="xalan://java.net.URLEncoder" exclude-result-prefixes="xalan strings encoder datetime"
                version="1.0" xmlns:strings="http://exslt.org/strings"
                xmlns:confman="org.dspace.core.ConfigurationManager">


    <xsl:template match="dri:referenceSet[@type = 'embeddedView']" priority="2">

        <xsl:apply-templates select="dri:head"/>

        <table style="border:1px solid black; background-color: #fafad2;"><tr><td>
            <xsl:apply-templates select="*[not(name()='head')]" mode="embeddedView"/>
        </td></tr></table>

    </xsl:template>

    <xsl:template match="dri:reference" mode="embeddedView">
        <xsl:variable name="externalMetadataURL">
            <xsl:text>cocoon:/</xsl:text>
            <xsl:value-of select="@url"/>
            <!-- No options selected, render the full METS document -->
        </xsl:variable>
        <xsl:comment>External Metadata URL:
            <xsl:value-of select="$externalMetadataURL"/>
        </xsl:comment>
        <xsl:apply-templates select="document($externalMetadataURL)" mode="embeddedView"/>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="mets:METS[mets:dmdSec/mets:mdWrap[@OTHERMDTYPE='DIM']]" mode="embeddedView">

        <xsl:variable name="my_doi" select=".//dim:field[@element='identifier'][not(@qualifier)][starts-with(., 'doi:')]" />

            <div style="padding-left:2px; padding-top:2px;">
                <!-- Title && Views && Downloads -->
                <span style="font-weight: bold;"><xsl:copy-of select=".//dim:field[@element='title']"/></span>

                <xsl:variable name="pageviews" select=".//dim:field[@element='dryad'][@qualifier='pageviews']"/>
                    <xsl:if test="$pageviews">
                        <span style="font-size: smaller; font-weight: bold;">
                            <xsl:text>&#160;&#160;&#160;</xsl:text>
                            <xsl:value-of select="$pageviews"/>
                            <xsl:choose>
                                <xsl:when test="string($pageviews) = '1'">
                                    <i18n:text>xmlui.DryadItemSummary.view</i18n:text>
                                </xsl:when>
                                <xsl:otherwise>
                                    <i18n:text>xmlui.DryadItemSummary.views</i18n:text>
                                </xsl:otherwise>
                            </xsl:choose>
                            <xsl:variable name="downloads"
                                          select=".//dim:field[@element='dryad'][@qualifier='downloads']"/>
                            <xsl:if test="$downloads">
                                <xsl:text>&#160;&#160;&#160;</xsl:text>
                                <xsl:value-of select="$downloads"/>
                                <xsl:choose>
                                    <xsl:when test="string($downloads) = '1'">
                                        <i18n:text>xmlui.DryadItemSummary.download</i18n:text>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <i18n:text>xmlui.DryadItemSummary.downloads</i18n:text>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:if>
                        </span>
                    </xsl:if>

                    <!-- View File Details -->
                    <a>
                        <xsl:attribute name="href">
                            <xsl:call-template name="checkURL">
                                <xsl:with-param name="url">
                                    <xsl:value-of
                                            select="concat('http://dx.doi.org/', substring-after($my_doi, 'doi:'))"/>
                                </xsl:with-param>
                                <xsl:with-param name="localize" select="$localize"/>
                            </xsl:call-template>
                        </xsl:attribute>
                        <xsl:text>View File Details</xsl:text>
                    </a>
            </div>

            <div style="padding-left:1px; padding-top:2px;">
                <span>
                    <xsl:copy-of select=".//dim:field[@element='description'][@mdschema='dc'][not(@qualifier)]"/>
                </span>
            </div>


            <!-- Bitstream List -->
            <div style="padding-left:2px; padding-top:2px;">
                <xsl:for-each select="/mets:METS/mets:fileSec/mets:fileGrp[@USE='CONTENT']/mets:file">
                    <!-- TITLE -->
                    <div>
                        <span>Download:</span>
                        <a>
                            <xsl:attribute name="href"><xsl:value-of select="mets:FLocat/@xlink:href"/></xsl:attribute>
                            <xsl:value-of select="mets:FLocat/@xlink:title"/>
                        </a>
                        <!-- SIZE -->
                        <span class="bitstream-filesize">(
                            <xsl:choose>
                                <xsl:when test="@SIZE &lt; 1000">
                                    <xsl:value-of select="@SIZE"/>
                                    <i18n:text>xmlui.dri2xhtml.METS-1.0.size-bytes</i18n:text>
                                </xsl:when>
                                <xsl:when test="@SIZE &lt; 1000000">
                                    <xsl:value-of select="substring(string(@SIZE div 1000),1,5)"/>
                                    <i18n:text>xmlui.dri2xhtml.METS-1.0.size-kilobytes</i18n:text>
                                </xsl:when>
                                <xsl:when test="@SIZE &lt; 1000000000">
                                    <xsl:value-of select="substring(string(@SIZE div 1000000),1,5)"/>
                                    <i18n:text>xmlui.dri2xhtml.METS-1.0.size-megabytes</i18n:text>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="substring(string(@SIZE div 1000000000),1,5)"/>
                                    <i18n:text>xmlui.dri2xhtml.METS-1.0.size-gigabytes</i18n:text>
                                </xsl:otherwise>
                            </xsl:choose>)
            </span>
                    </div>
                </xsl:for-each>
            </div>

            <!-- cc-zero.png && opendata.png -->
            <div style="padding-left:1px; padding-top:2px;">
                <i18n:text>xmlui.dri2xhtml.METS-1.0.license-cc0</i18n:text>
                <xsl:text> &#160; </xsl:text>
                <a href="http://creativecommons.org/publicdomain/zero/1.0/" target="_blank">
                    <img src="/themes/Dryad/images/cc-zero.png" />
                </a>
                <a href="http://opendefinition.org/">
                    <img src="/themes/Dryad/images/opendata.png" />
                </a>
            </div>


            <br/>
            <br/>
    </xsl:template>


</xsl:stylesheet>