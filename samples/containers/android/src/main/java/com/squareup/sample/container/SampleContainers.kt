package com.squareup.sample.container

import com.squareup.sample.container.overviewdetail.OverviewDetailContainer
import com.squareup.sample.container.panel.PanelContainer
import com.squareup.sample.container.panel.ScrimContainer
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewRegistry

@OptIn(WorkflowUiExperimentalApi::class)
val SampleContainers = ViewRegistry(
    BackButtonViewFactory, OverviewDetailContainer, PanelContainer, ScrimContainer
)
