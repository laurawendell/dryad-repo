<!--

    The contents of this file are subject to the license and copyright
    detailed in the LICENSE and NOTICE files at the root of the source
    tree and available online at

    http://www.dspace.org/license/

-->
<!--
    Main structure of the page, determines where
    header, footer, body, navigation are structurally rendered.
    Rendering of the header, footer, trail and alerts

    Author: art.lowel at atmire.com
    Author: lieven.droogmans at atmire.com
    Author: ben at atmire.com
    Author: Alexey Maslov

-->

<xsl:stylesheet xmlns:i18n="http://apache.org/cocoon/i18n/2.1"
	xmlns:dri="http://di.tamu.edu/DRI/1.0/"
	xmlns:mets="http://www.loc.gov/METS/"
	xmlns:xlink="http://www.w3.org/TR/xlink/"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
	xmlns:dim="http://www.dspace.org/xmlns/dspace/dim"
	xmlns:xhtml="http://www.w3.org/1999/xhtml"
	xmlns:mods="http://www.loc.gov/mods/v3"
	xmlns:dc="http://purl.org/dc/elements/1.1/"
	xmlns="http://www.w3.org/1999/xhtml"
	exclude-result-prefixes="i18n dri mets xlink xsl dim xhtml mods dc">

    <xsl:output indent="yes"/>

    <!--
        The starting point of any XSL processing is matching the root element. In DRI the root element is document,
        which contains a version attribute and three top level elements: body, options, meta (in that order).

        This template creates the html document, giving it a head and body. A title and the CSS style reference
        are placed in the html head, while the body is further split into several divs. The top-level div
        directly under html body is called "ds-main". It is further subdivided into:
            "ds-header"  - the header div containing title, subtitle, trail and other front matter
            "ds-body"    - the div containing all the content of the page; built from the contents of dri:body
            "ds-options" - the div with all the navigation and actions; built from the contents of dri:options
            "ds-footer"  - optional footer div, containing misc information

        The order in which the top level divisions appear may have some impact on the design of CSS and the
        final appearance of the DSpace page. While the layout of the DRI schema does favor the above div
        arrangement, nothing is preventing the designer from changing them around or adding new ones by
        overriding the dri:document template.
    -->
    <xsl:template match="dri:document">
        <html>
            <!-- First of all, build the HTML head element -->
            <xsl:call-template name="buildHead"/>
            <!-- Then proceed to the body -->
            <xsl:choose>
              <xsl:when test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='framing'][@qualifier='popup']">
                <xsl:apply-templates select="dri:body/*"/>
                <!-- add setup JS code if this is a choices lookup page -->
                <xsl:if test="dri:body/dri:div[@n='lookup']">
                  <xsl:call-template name="choiceLookupPopUpSetup"/>
                </xsl:if>
              </xsl:when>
              <xsl:otherwise>
            <body>

                <div id="ds-main">
                    <!--
                        The header div, complete with title, subtitle, trail and other junk. The trail is
                        built by applying a template over pageMeta's trail children. -->
                    <xsl:call-template name="buildHeader"/>

                    <!--
                        Goes over the document tag's children elements: body, options, meta. The body template
                        generates the ds-body div that contains all the content. The options template generates
                        the ds-options div that contains the navigation and action options available to the
                        user. The meta element is ignored since its contents are not processed directly, but
                        instead referenced from the different points in the document. -->
                    <xsl:apply-templates />

                    <!--
                        The footer div, dropping whatever extra information is needed on the page. It will
                        most likely be something similar in structure to the currently given example. -->
                    <xsl:call-template name="buildFooter"/>

                </div>
            </body>
              </xsl:otherwise>
            </xsl:choose>
        </html>
    </xsl:template>

        <!-- The HTML head element contains references to CSS as well as embedded JavaScript code. Most of this
        information is either user-provided bits of post-processing (as in the case of the JavaScript), or
        references to stylesheets pulled directly from the pageMeta element. -->
    <xsl:template name="buildHead">
        <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
            <meta name="Generator">
              <xsl:attribute name="content">
                <xsl:text>DSpace</xsl:text>
                <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='dspace'][@qualifier='version']">
                  <xsl:text> </xsl:text>
                  <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='dspace'][@qualifier='version']"/>
                </xsl:if>
              </xsl:attribute>
            </meta>
            <!-- Add stylsheets -->
            <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='stylesheet']">
                <link rel="stylesheet" type="text/css">
                    <xsl:attribute name="media">
                        <xsl:value-of select="@qualifier"/>
                    </xsl:attribute>
                    <xsl:attribute name="href">
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath'][not(@qualifier)]"/>
                        <xsl:text>/themes/</xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='theme'][@qualifier='path']"/>
                        <xsl:text>/</xsl:text>
                        <xsl:value-of select="."/>
                    </xsl:attribute>
                </link>
            </xsl:for-each>

            <!-- Add syndication feeds -->
            <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='feed']">
                <link rel="alternate" type="application">
                    <xsl:attribute name="type">
                        <xsl:text>application/</xsl:text>
                        <xsl:value-of select="@qualifier"/>
                    </xsl:attribute>
                    <xsl:attribute name="href">
                        <xsl:value-of select="."/>
                    </xsl:attribute>
                </link>
            </xsl:for-each>

            <!--  Add OpenSearch auto-discovery link -->
            <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='opensearch'][@qualifier='shortName']">
                <link rel="search" type="application/opensearchdescription+xml">
                    <xsl:attribute name="href">
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='scheme']"/>
                        <xsl:text>://</xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='serverName']"/>
                        <xsl:text>:</xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='serverPort']"/>
                        <xsl:value-of select="$context-path"/>
                        <xsl:text>/</xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='opensearch'][@qualifier='context']"/>
                        <xsl:text>description.xml</xsl:text>
                    </xsl:attribute>
                    <xsl:attribute name="title" >
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='opensearch'][@qualifier='shortName']"/>
                    </xsl:attribute>
                </link>
            </xsl:if>

            <!-- The following javascript removes the default text of empty text areas when they are focused on or submitted -->
            <!-- There is also javascript to disable submitting a form when the 'enter' key is pressed. -->
                        <script type="text/javascript">
                                //Clear default text of emty text areas on focus
                                function tFocus(element)
                                {
                                        if (element.value == '<i18n:text>xmlui.dri2xhtml.default.textarea.value</i18n:text>'){element.value='';}
                                }
                                //Clear default text of emty text areas on submit
                                function tSubmit(form)
                                {
                                        var defaultedElements = document.getElementsByTagName("textarea");
                                        for (var i=0; i != defaultedElements.length; i++){
                                                if (defaultedElements[i].value == '<i18n:text>xmlui.dri2xhtml.default.textarea.value</i18n:text>'){
                                                        defaultedElements[i].value='';}}
                                }
                                //Disable pressing 'enter' key to submit a form (otherwise pressing 'enter' causes a submission to start over)
                                function disableEnterKey(e)
                                {
                                     var key;

                                     if(window.event)
                                          key = window.event.keyCode;     //Internet Explorer
                                     else
                                          key = e.which;     //Firefox and Netscape

                                     if(key == 13)  //if "Enter" pressed, then disable!
                                          return false;
                                     else
                                          return true;
                                }
            </script>

            <!-- Add theme javascipt  -->
            <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='javascript'][not(@qualifier)]">
                <script type="text/javascript">
                    <xsl:attribute name="src">
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath'][not(@qualifier)]"/>
                        <xsl:text>/themes/</xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='theme'][@qualifier='path']"/>
                        <xsl:text>/</xsl:text>
                        <xsl:value-of select="."/>
                    </xsl:attribute>&#160;</script>
            </xsl:for-each>

            <!-- add "shared" javascript from static, path is relative to webapp root-->
            <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='javascript'][@qualifier='static']">
                <script type="text/javascript">
                    <xsl:attribute name="src">
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath'][not(@qualifier)]"/>
                        <xsl:text>/</xsl:text>
                        <xsl:value-of select="."/>
                    </xsl:attribute>&#160;</script>
            </xsl:for-each>


            <!-- Add a google analytics script if the key is present -->
            <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='google'][@qualifier='analytics']">
                <script type="text/javascript"><xsl:text>
                       var _gaq = _gaq || [];
                       _gaq.push(['_setAccount', '</xsl:text><xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='google'][@qualifier='analytics']"/><xsl:text>']);
                       _gaq.push(['_trackPageview']);

                       (function() {
                           var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
                           ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
                           var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
                       })();
               </xsl:text></script>
            </xsl:if>


            <!-- Add the title in -->
            <xsl:variable name="page_title" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='title']" />
            <title>
                <xsl:choose>
                        <xsl:when test="not($page_title)">
                                <xsl:text>  </xsl:text>
                        </xsl:when>
                        <xsl:otherwise>
                                <xsl:copy-of select="$page_title/node()" />
                        </xsl:otherwise>
                </xsl:choose>
            </title>

            <!-- Head metadata in item pages -->
            <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='xhtml_head_item']">
                <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='xhtml_head_item']"
                              disable-output-escaping="yes"/>
            </xsl:if>

            <!-- Add all Google Scholar Metadata values -->
            <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[substring(@element, 1, 9) = 'citation_']">
                <meta name="{@element}" content="{.}"></meta>
            </xsl:for-each>

        </head>
    </xsl:template>


    <!-- The header (distinct from the HTML head element) contains the title, subtitle, login box and various
        placeholders for header images -->
    <xsl:template name="buildHeader">
        <div id="ds-header">
            <a>
                <xsl:attribute name="href">
                    <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath'][not(@qualifier)]"/>
                    <xsl:text>/</xsl:text>
                </xsl:attribute>
                <span id="ds-header-logo">&#160;</span>
            </a>
            <h1 class="pagetitle">
                <xsl:choose>
                        <!-- protectiotion against an empty page title -->
                        <xsl:when test="not(/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='title'])">
                                <xsl:text> </xsl:text>
                        </xsl:when>
                        <xsl:otherwise>
                                <xsl:copy-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='title']/node()"/>
                        </xsl:otherwise>
                </xsl:choose>

            </h1>
            <h2 class="static-pagetitle"><i18n:text>xmlui.dri2xhtml.structural.head-subtitle</i18n:text></h2>


            <ul id="ds-trail">
                <xsl:choose>
                        <xsl:when test="count(/dri:document/dri:meta/dri:pageMeta/dri:trail) = 0">
                                <li class="ds-trail-link first-link"> - </li>
                        </xsl:when>
                        <xsl:otherwise>
                                <xsl:apply-templates select="/dri:document/dri:meta/dri:pageMeta/dri:trail"/>
                        </xsl:otherwise>
                </xsl:choose>
            </ul>


            <xsl:choose>
                <xsl:when test="/dri:document/dri:meta/dri:userMeta/@authenticated = 'yes'">
                    <div id="ds-user-box">
                        <p>
                            <a>
                                <xsl:attribute name="href">
                                    <xsl:value-of select="/dri:document/dri:meta/dri:userMeta/
                                        dri:metadata[@element='identifier' and @qualifier='url']"/>
                                </xsl:attribute>
                                <i18n:text>xmlui.dri2xhtml.structural.profile</i18n:text>
                                <xsl:value-of select="/dri:document/dri:meta/dri:userMeta/
                                    dri:metadata[@element='identifier' and @qualifier='firstName']"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="/dri:document/dri:meta/dri:userMeta/
                                    dri:metadata[@element='identifier' and @qualifier='lastName']"/>
                            </a>
                            <xsl:text> | </xsl:text>
                            <a>
                                <xsl:attribute name="href">
                                    <xsl:value-of select="/dri:document/dri:meta/dri:userMeta/
                                        dri:metadata[@element='identifier' and @qualifier='logoutURL']"/>
                                </xsl:attribute>
                                <i18n:text>xmlui.dri2xhtml.structural.logout</i18n:text>
                            </a>
                        </p>
                    </div>
                </xsl:when>
                <xsl:otherwise>
                    <div id="ds-user-box">
                        <p>
                            <a>
                                <xsl:attribute name="href">
                                    <xsl:value-of select="/dri:document/dri:meta/dri:userMeta/
                                        dri:metadata[@element='identifier' and @qualifier='loginURL']"/>
                                </xsl:attribute>
                                <i18n:text>xmlui.dri2xhtml.structural.login</i18n:text>
                            </a>
                        </p>
                    </div>
                </xsl:otherwise>
            </xsl:choose>

        </div>
    </xsl:template>


    <!-- Like the header, the footer contains various miscellanious text, links, and image placeholders -->
    <xsl:template name="buildFooter">
        <div id="ds-footer">
            <i18n:text>xmlui.dri2xhtml.structural.footer-promotional</i18n:text>
            <div id="ds-footer-links">
                <a>
                    <xsl:attribute name="href">
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath'][not(@qualifier)]"/>
                        <xsl:text>/contact</xsl:text>
                    </xsl:attribute>
                    <i18n:text>xmlui.dri2xhtml.structural.contact-link</i18n:text>
                </a>
                <xsl:text> | </xsl:text>
                <a>
                    <xsl:attribute name="href">
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath'][not(@qualifier)]"/>
                        <xsl:text>/feedback</xsl:text>
                    </xsl:attribute>
                    <i18n:text>xmlui.dri2xhtml.structural.feedback-link</i18n:text>
                </a>
            </div>
            <!--Invisible link to HTML sitemap (for search engines) -->
            <a>
                <xsl:attribute name="href">
                    <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath'][not(@qualifier)]"/>
                    <xsl:text>/htmlmap</xsl:text>
                </xsl:attribute>
            </a>
        </div>
        <!--
            <a href="http://di.tamu.edu">
                <div id="ds-footer-logo"></div>
            </a>
            <p>
            This website is using Manakin, a new front end for DSpace created by Texas A&amp;M University
            Libraries. The interface can be extensively modified through Manakin Aspects and XSL based Themes.
            For more information visit
            <a href="http://di.tamu.edu">http://di.tamu.edu</a> and
            <a href="http://dspace.org">http://dspace.org</a>
            </p>
        -->
    </xsl:template>



    <!--
        The trail is built one link at a time. Each link is given the ds-trail-link class, with the first and
        the last links given an additional descriptor.
    -->
    <xsl:template match="dri:trail">
        <li>
            <xsl:attribute name="class">
                <xsl:text>ds-trail-link </xsl:text>
                <xsl:if test="position()=1">
                    <xsl:text>first-link </xsl:text>
                </xsl:if>
                <xsl:if test="position()=last()">
                    <xsl:text>last-link</xsl:text>
                </xsl:if>
            </xsl:attribute>
            <!-- Determine whether we are dealing with a link or plain text trail link -->
            <xsl:choose>
                <xsl:when test="./@target">
                    <a>
                        <xsl:attribute name="href">
                            <xsl:value-of select="./@target"/>
                        </xsl:attribute>
                        <xsl:apply-templates />
                    </a>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates />
                </xsl:otherwise>
            </xsl:choose>
        </li>
    </xsl:template>




<!--
        The meta, body, options elements; the three top-level elements in the schema
-->




    <!--
        The template to handle the dri:body element. It simply creates the ds-body div and applies
        templates of the body's child elements (which consists entirely of dri:div tags).
    -->
    <xsl:template match="dri:body">
        <div id="ds-body">
            <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='alert'][@qualifier='message']">
                <div id="ds-system-wide-alert">
                    <p>
                        <xsl:copy-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='alert'][@qualifier='message']/node()"/>
                    </p>
                </div>
            </xsl:if>
            <xsl:apply-templates />
        </div>
    </xsl:template>


    <!-- Currently the dri:meta element is not parsed directly. Instead, parts of it are referenced from inside
        other elements (like reference). The blank template below ends the execution of the meta branch -->
    <xsl:template match="dri:meta">
    </xsl:template>

    <!-- Meta's children: userMeta, pageMeta, objectMeta and repositoryMeta may or may not have templates of
        their own. This depends on the meta template implementation, which currently does not go this deep.
    <xsl:template match="dri:userMeta" />
    <xsl:template match="dri:pageMeta" />
    <xsl:template match="dri:objectMeta" />
    <xsl:template match="dri:repositoryMeta" />
    -->
</xsl:stylesheet>
