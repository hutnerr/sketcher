package com.sketcher.ui.common;

/** Implemented by views that need to reload data whenever the user navigates back to them. */
public interface Refreshable {
    void onShown();
}
