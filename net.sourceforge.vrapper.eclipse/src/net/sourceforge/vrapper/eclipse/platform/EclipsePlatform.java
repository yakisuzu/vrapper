package net.sourceforge.vrapper.eclipse.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import net.sourceforge.vrapper.eclipse.keymap.AbstractEclipseSpecificStateProvider;
import net.sourceforge.vrapper.eclipse.keymap.UnionStateProvider;
import net.sourceforge.vrapper.eclipse.mode.AbstractEclipseSpecificModeProvider;
import net.sourceforge.vrapper.eclipse.mode.UnionModeProvider;
import net.sourceforge.vrapper.eclipse.utils.Utils;
import net.sourceforge.vrapper.log.VrapperLog;
import net.sourceforge.vrapper.platform.CursorService;
import net.sourceforge.vrapper.platform.FileService;
import net.sourceforge.vrapper.platform.GlobalConfiguration;
import net.sourceforge.vrapper.platform.HighlightingService;
import net.sourceforge.vrapper.platform.HistoryService;
import net.sourceforge.vrapper.platform.Platform;
import net.sourceforge.vrapper.platform.PlatformSpecificModeProvider;
import net.sourceforge.vrapper.platform.PlatformSpecificStateProvider;
import net.sourceforge.vrapper.platform.PlatformSpecificTextObjectProvider;
import net.sourceforge.vrapper.platform.SearchAndReplaceService;
import net.sourceforge.vrapper.platform.ServiceProvider;
import net.sourceforge.vrapper.platform.TextContent;
import net.sourceforge.vrapper.platform.UnderlyingEditorSettings;
import net.sourceforge.vrapper.platform.UserInterfaceService;
import net.sourceforge.vrapper.platform.ViewportService;
import net.sourceforge.vrapper.utils.DefaultKeyMapProvider;
import net.sourceforge.vrapper.vim.TextObjectProvider;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension6;
import org.eclipse.jface.text.IUndoManager;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.part.MultiPageEditorSite;
import org.eclipse.ui.texteditor.AbstractTextEditor;

public class EclipsePlatform implements Platform {

    private final EclipseCursorAndSelection cursorAndSelection;
    private final EclipseTextContent textContent;
    private final EclipseFileService fileService;
    private final EclipseViewportService viewportService;
    private final HistoryService historyService;
    private final EclipseServiceProvider serviceProvider;
    private final EclipseUserInterfaceService userInterfaceService;
    private final DefaultKeyMapProvider keyMapProvider;
    private final UnderlyingEditorSettings underlyingEditorSettings;
    private final GlobalConfiguration configuration;
    private final AbstractTextEditor underlyingEditor;
    private final HighlightingService highlightingService;
    private final SearchAndReplaceService searchAndReplaceService;
    private static final Map<String, PlatformSpecificStateProvider> providerCache = new ConcurrentHashMap<String, PlatformSpecificStateProvider>();
    private static final AtomicReference<PlatformSpecificModeProvider> modeProviderCache= new AtomicReference<PlatformSpecificModeProvider>();
    private static final Map<String, PlatformSpecificTextObjectProvider> textObjProviderCache = new ConcurrentHashMap<String, PlatformSpecificTextObjectProvider>();

    public EclipsePlatform(final AbstractTextEditor abstractTextEditor,
            final ITextViewer textViewer, final GlobalConfiguration sharedConfiguration) {
        underlyingEditor = abstractTextEditor;
        configuration = sharedConfiguration;
        textContent = new EclipseTextContent(textViewer);
        cursorAndSelection = new EclipseCursorAndSelection(configuration,
                textViewer, textContent);
        fileService = new EclipseFileService(abstractTextEditor);
        viewportService = new EclipseViewportService(textViewer);
        serviceProvider = new EclipseServiceProvider(abstractTextEditor);
        userInterfaceService = new EclipseUserInterfaceService(
                abstractTextEditor, textViewer);
        keyMapProvider = new DefaultKeyMapProvider();
        underlyingEditorSettings = new AbstractTextEditorSettings(
                abstractTextEditor);
        highlightingService = new EclipseHighlightingService(abstractTextEditor, cursorAndSelection);
        searchAndReplaceService = new EclipseSearchAndReplaceService(textViewer, sharedConfiguration, highlightingService);
        if (textViewer instanceof ITextViewerExtension6) {
            final IUndoManager delegate = ((ITextViewerExtension6) textViewer)
                    .getUndoManager();
            final EclipseHistoryService manager = new EclipseHistoryService(
                    textViewer.getTextWidget(), delegate);
            textViewer.setUndoManager(manager);
            this.historyService = manager;
        } else {
            this.historyService = new DummyHistoryService();
        }
    }

    @Override
    public CursorService getCursorService() {
        return cursorAndSelection;
    }

    @Override
    public TextContent getModelContent() {
        return textContent.getModelContent();
    }

    @Override
    public EclipseCursorAndSelection getSelectionService() {
        return cursorAndSelection;
    }

    @Override
    public TextContent getViewContent() {
        return textContent.getViewContent();
    }

    @Override
    public FileService getFileService() {
        return fileService;
    }

    @Override
    public ViewportService getViewportService() {
        return viewportService;
    }

    @Override
    public HistoryService getHistoryService() {
        return historyService;
    }

    @Override
    public ServiceProvider getServiceProvider() {
        return serviceProvider;
    }

    @Override
    public UserInterfaceService getUserInterfaceService() {
        return userInterfaceService;
    }

    @Override
    public DefaultKeyMapProvider getKeyMapProvider() {
        return keyMapProvider;
    }

    @Override
    public UnderlyingEditorSettings getUnderlyingEditorSettings() {
        return underlyingEditorSettings;
    }

    @Override
    public GlobalConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public PlatformSpecificStateProvider getPlatformSpecificStateProvider(
            TextObjectProvider textObjectProvider) {
        final String className = underlyingEditor.getClass().getName();
        if (!providerCache.containsKey(className)) {
            providerCache.put(className, buildPlatformSpecificStateProvider(textObjectProvider));
        }
        return providerCache.get(className);
    }

    @Override
    public PlatformSpecificModeProvider getPlatformSpecificModeProvider() {
        if (modeProviderCache.get() == null) {
            final PlatformSpecificModeProvider provider = buildPlatformSpecificModeProvider();
            // Only set this once. Custom modes don't use editor-specific test.
            modeProviderCache.compareAndSet(null, provider);
        }
        return modeProviderCache.get();
    }

    @Override
    public PlatformSpecificTextObjectProvider getPlatformSpecificTextObjectProvider() {
        final String className = underlyingEditor.getClass().getName();
        if (!textObjProviderCache.containsKey(className)) {
            textObjProviderCache.put(className, buildPlatformSpecificTextObjectProvider());
        }
        return textObjProviderCache.get(className);
    }

    @Override
    public SearchAndReplaceService getSearchAndReplaceService() {
        return searchAndReplaceService;
    }

    private PlatformSpecificStateProvider buildPlatformSpecificStateProvider(
            TextObjectProvider textObjectProvider) {
        final IExtensionRegistry registry = org.eclipse.core.runtime.Platform
                .getExtensionRegistry();
        final IConfigurationElement[] elements = registry
                .getConfigurationElementsFor("net.sourceforge.vrapper.eclipse.pssp");
        final List<AbstractEclipseSpecificStateProvider> matched = new ArrayList<AbstractEclipseSpecificStateProvider>();
        for (final IConfigurationElement element : elements) {
            final AbstractEclipseSpecificStateProvider provider = (AbstractEclipseSpecificStateProvider) Utils
                    .createGizmoForElementConditionally(
                            underlyingEditor, "editor-must-subclass",
                            element, "provider-class");
            if (provider != null) {
                provider.initializeProvider(textObjectProvider);
                matched.add(provider);
            }
        }
        Collections.sort(matched);
        return new UnionStateProvider("extensions for "
                + underlyingEditor.getClass().getName(), matched);
    }

    private PlatformSpecificModeProvider buildPlatformSpecificModeProvider() {
        final IExtensionRegistry registry = org.eclipse.core.runtime.Platform.getExtensionRegistry();
        final IConfigurationElement[] elements = registry
                .getConfigurationElementsFor("net.sourceforge.vrapper.eclipse.psmp");
        final List<AbstractEclipseSpecificModeProvider> matched = new ArrayList<AbstractEclipseSpecificModeProvider>();
        for (final IConfigurationElement element : elements) {
            try {
                matched.add((AbstractEclipseSpecificModeProvider)
                        element.createExecutableExtension("provider-class"));
            } catch (final Exception e) {
                VrapperLog.error("error while building mode providers", e);
            }
        }
        return new UnionModeProvider("Extension modes", matched);
    }

    private PlatformSpecificTextObjectProvider buildPlatformSpecificTextObjectProvider() {
        final IExtensionRegistry registry = org.eclipse.core.runtime.Platform
                .getExtensionRegistry();
        final IConfigurationElement[] elements = registry
                .getConfigurationElementsFor("net.sourceforge.vrapper.eclipse.pstop");
        final List<PlatformSpecificTextObjectProvider> matched = new ArrayList<PlatformSpecificTextObjectProvider>();
        for (final IConfigurationElement element : elements) {
            final PlatformSpecificTextObjectProvider provider =
                    (PlatformSpecificTextObjectProvider) Utils.createGizmoForElementConditionally(
                            underlyingEditor, "editor-must-subclass",
                            element, "provider-class");
            if (provider != null) {
                matched.add(provider);
            }
        }
        // Priority code is not implemented. If it was, add a SortKey decorator.
        // Collections.sort(matched);
        return new UnionTextObjectProvider("extensions for "
                + underlyingEditor.getClass().getName(), matched);
    }

    public String getEditorType() {
    	IWorkbenchPartSite site = underlyingEditor.getSite();
    	if(site instanceof MultiPageEditorSite) {
    		site = ((MultiPageEditorSite)site).getMultiPageEditor().getSite();
    	}
    		
        return site.getRegisteredName();
    }

    @Override
    public HighlightingService getHighlightingService() {
        return highlightingService;
    }

}
