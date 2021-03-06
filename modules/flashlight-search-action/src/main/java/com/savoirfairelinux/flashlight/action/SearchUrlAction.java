// Copyright 2017 Savoir-faire Linux
// This file is part of Flashlight Search.

// Flashlight Search is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// Flashlight Search is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with Flashlight Search.  If not, see <http://www.gnu.org/licenses/>.

package com.savoirfairelinux.flashlight.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.portlet.PortletMode;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.liferay.portal.kernel.events.Action;
import com.liferay.portal.kernel.events.ActionException;
import com.liferay.portal.kernel.events.LifecycleAction;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutConstants;
import com.liferay.portal.kernel.model.LayoutTypePortlet;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.WebKeys;
import com.savoirfairelinux.flashlight.service.model.SearchUrl;
import com.savoirfairelinux.flashlight.service.model.SearchUrlContainer;
import com.savoirfairelinux.flashlight.service.model.SearchUrlRequestParameter;
import com.savoirfairelinux.flashlight.service.portlet.FlashlightSearchPortletKeys;

/**
 * This action is used to insert the possible search URLs in the request attributes. This way, a search box can be
 * placed in the theme, without the need to put embedded portlets.
 */
@Component(
    service = LifecycleAction.class,
    property = { "key=servlet.service.events.pre" }
)
public class SearchUrlAction extends Action {

    public static final String REQUEST_ATTR_FLASHLIGHT_URLS = FlashlightSearchPortletKeys.PORTLET_NAME + "_urls";

    private static final String PARAM_PORTLET_ID = "p_p_id";
    private static final String PARAM_PORTLET_LIFECYCLE = "p_p_lifecycle";
    private static final String PARAM_PORTLET_MODE = "p_p_mode";
    private static final String PARAM_PORTLET_COLUMN_ID = "p_p_col_id";
    private static final String PARAM_PORTLET_COLUMN_COUNT = "p_p_col_count";

    private static final String LIFECYCLE_RENDER = "0";

    private static final Log LOG = LogFactoryUtil.getLog(SearchUrlAction.class);

    @Reference
    private Portal portal;

    @Reference
    private LayoutLocalService layoutService;

    @Override
    public void run(HttpServletRequest request, HttpServletResponse response) throws ActionException {
        ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);
        Layout currentLayout = themeDisplay.getLayout();


        List<Layout> searchLayouts = this.getSearchLayouts(currentLayout);
        Map<Layout, List<SearchUrl>> searchUrls;

        searchUrls = searchLayouts.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                layout -> generateSearchUrl(request, themeDisplay, layout)
            ));

        request.setAttribute(REQUEST_ATTR_FLASHLIGHT_URLS, new SearchUrlContainer(searchUrls));
    }

    /**
     * Returns a list of search URLs to be put in the request attributes
     *
     * @param themeDisplay The theme display
     * @param layout The current page
     * @return A list of search URLs to be put in the request attributes
     */
    private List<SearchUrl> generateSearchUrl(HttpServletRequest request, ThemeDisplay themeDisplay, Layout layout) {
        LayoutTypePortlet layoutType = (LayoutTypePortlet) layout.getLayoutType();
        UnicodeProperties props = layoutType.getTypeSettingsProperties();

        return layoutType.getPortlets().stream()
            .filter(portlet -> portlet.getPortletName().equals(FlashlightSearchPortletKeys.PORTLET_NAME))
            .map(portletInstance -> {

                String columnId = StringPool.BLANK;
                for(Entry<String, String> entry : props.entrySet()) {
                    if(entry.getValue().equals(portletInstance.getPortletId())) {
                        columnId = entry.getKey();
                        break;
                    }
                }

                String portletUrl;

                try {
                    portletUrl = this.portal.getLayoutFriendlyURL(layout, themeDisplay);
                } catch (PortalException e) {
                    portletUrl = StringPool.BLANK;
                    LOG.error(e);
                }

                SearchUrlRequestParameter[] params = new SearchUrlRequestParameter[] {
                    new SearchUrlRequestParameter(PARAM_PORTLET_ID, portletInstance.getPortletId()),
                    new SearchUrlRequestParameter(PARAM_PORTLET_LIFECYCLE, LIFECYCLE_RENDER),
                    new SearchUrlRequestParameter(PARAM_PORTLET_MODE, PortletMode.VIEW.toString()),
                    new SearchUrlRequestParameter(PARAM_PORTLET_COLUMN_ID, columnId),
                    new SearchUrlRequestParameter(PARAM_PORTLET_COLUMN_COUNT, Integer.toString(layoutType.getNumOfColumns()))
                };

                String portletNamespace = this.portal.getPortletNamespace(portletInstance.getPortletId());

                return new SearchUrl(layout, portletUrl, params, portletNamespace);
            })
            .collect(Collectors.toList());
    }

    /**
     * Returns a list of layouts that contains the search portlet. Only layouts with the same private/public status and
     * groupId as the given layout are searched for.
     *
     * @param currentLayout The layout from which to start the search.
     * @return The list of layouts that contains the search portlet
     */
    private List<Layout> getSearchLayouts(Layout currentLayout) {
        long groupId = currentLayout.getGroupId();
        boolean privateLayout = currentLayout.isPrivateLayout();
        List<Layout> siteLayouts = this.layoutService.getLayouts(groupId, privateLayout, LayoutConstants.TYPE_PORTLET);

        List<Layout> searchLayouts = new ArrayList<>();
        for(Layout layout : siteLayouts) {
            // We can safely type cast because we specify the layout type in the query above
            LayoutTypePortlet layoutType = (LayoutTypePortlet) layout.getLayoutType();
            if(layoutType.hasPortletId(FlashlightSearchPortletKeys.PORTLET_NAME)) {
                searchLayouts.add(layout);
            }
        }

        return searchLayouts;
    }

}
