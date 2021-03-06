package com.centurylink.mdw.studio.config

import com.centurylink.mdw.draw.RoundedBorder
import com.centurylink.mdw.draw.edit.*
import com.centurylink.mdw.draw.model.WorkflowObj
import com.centurylink.mdw.model.asset.Pagelet
import com.centurylink.mdw.studio.config.widgets.Editor
import com.centurylink.mdw.studio.config.widgets.Label
import com.centurylink.mdw.studio.config.widgets.SwingWidget
import com.centurylink.mdw.studio.config.widgets.Table
import com.google.gson.GsonBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import javax.swing.*

class ConfigTab(private val tabName: String, private val template: Template, val workflowObj: WorkflowObj) :
        JPanel(BorderLayout()), UpdateListeners by UpdateListenersDelegate() {

    private var containerPane = JPanel()
    private var scrollPane: JBScrollPane? = null
    private val allSwingWidgets = mutableMapOf<String,SwingWidget>()

    init {
        background = getBackgroundColor()
        initWidgets()
    }

    private fun initWidgets() {
        containerPane.background = getBackgroundColor()
        allSwingWidgets.clear()

        // println("PAGELET: " + GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(template.pagelet))

        try {
            val widgets = template.filterWidgets(tabName)
            widgets.forEach {
                it.init(template.category, workflowObj)
            }
            addWidgets(widgets)
        }
        catch (ex: Exception) {
            ex.printStackTrace()
            // show stack trace in containerPane
            val outputStream = ByteArrayOutputStream()
            ex.printStackTrace(PrintStream(outputStream))
            val textArea = JTextArea(String(outputStream.toByteArray()))
            textArea.isEditable = false
            textArea.isOpaque = false
            textArea.lineWrap = false
            textArea.font = Font("monospaced", Font.PLAIN, 12)
            containerPane.removeAll()
            containerPane.add(textArea)
            if (scrollPane == null) {
                addScrollPane()
            }
        }
    }

    private fun addScrollPane() {
        containerPane.layout = GridBagLayout()
        containerPane.border = BorderFactory.createEmptyBorder(0, 5, 0, 5)

        scrollPane = JBScrollPane(containerPane)
        scrollPane?.let {
            it.border = RoundedBorder(JBColor.border())
            it.isOpaque = false
            it.viewport.isOpaque = false
            add(it)
        }
    }

    private fun addWidgets(widgets: List<Pagelet.Widget>) {
        findSoloWidget(widgets,"editor")?.let {
            containerPane.layout = BorderLayout()
            containerPane.border = RoundedBorder(JBColor.border())
            val editor = Editor(it)
            editor.addUpdateListener { obj ->
                notifyUpdateListeners(obj)
            }
            containerPane.add(editor)
            add(containerPane)
            return
        }
        findSoloWidget(widgets,"table")?.let {
            val table = Table(it, true)
            table.border = RoundedBorder(JBColor.border())
            table.addUpdateListener { obj ->
                notifyUpdateListeners(obj)
            }
            add(table)
            return
        }

        if (!widgets.isEmpty()) {
            addScrollPane()

            val containerConstraints = GridBagConstraints()
            containerConstraints.anchor = GridBagConstraints.NORTH
            containerConstraints.gridx = 0
            containerConstraints.gridy = 0
            containerConstraints.fill = GridBagConstraints.HORIZONTAL
            containerConstraints.weightx = 1.0
            containerConstraints.weighty = 1.0

            val tableWidgets = widgets.filter { it.isTableType }
            val regularWidgets = widgets.filter { !it.isTableType }

            if (widgets[0].isTableType) {
                // all tables come first
                addTableWidgets(tableWidgets, containerConstraints)
                containerConstraints.gridy = 1
                addRegularWidgets(regularWidgets, containerConstraints)
            }
            else {
                // all tables come last
                addRegularWidgets(regularWidgets, containerConstraints)
                containerConstraints.gridy = 1
                addTableWidgets(tableWidgets, containerConstraints)
            }

            containerConstraints.gridy = 2
            containerConstraints.fill = GridBagConstraints.VERTICAL
            containerConstraints.gridheight = GridBagConstraints.REMAINDER
            containerConstraints.weighty = 100.0
            val glue = Box.createVerticalGlue()
            containerPane.add(glue, containerConstraints)
        }
    }

    private fun addRegularWidgets(regularWidgets: List<Pagelet.Widget>, constraints: GridBagConstraints) {
        if (!regularWidgets.isEmpty()) {
            val layout = GridBagLayout()
            val gridPanel = JPanel(layout)
            gridPanel.background = getBackgroundColor()

            val labelConstraints = GridBagConstraints()
            labelConstraints.gridx = 0
            labelConstraints.anchor = GridBagConstraints.NORTHWEST
            labelConstraints.fill = GridBagConstraints.NONE
            labelConstraints.weightx = 0.1

            val widgetConstraints = GridBagConstraints()
            labelConstraints.gridx = 1
            widgetConstraints.gridwidth = GridBagConstraints.REMAINDER
            widgetConstraints.anchor = GridBagConstraints.EAST
            widgetConstraints.fill = GridBagConstraints.HORIZONTAL
            widgetConstraints.weightx = 1.0

            for (widget in regularWidgets) {
                val label = Label(widget)
                gridPanel.add(label, labelConstraints)
                val swingWidget = createSwingWidget(widget)
                gridPanel.add(swingWidget, widgetConstraints)
            }

            // add gridPanel at the end to avoid adding when exception
            containerPane.add(gridPanel, constraints)
        }
    }

    private fun addTableWidgets(tableWidgets: List<Pagelet.Widget>, constraints: GridBagConstraints) {
        if (!tableWidgets.isEmpty()) {
            for (widget in tableWidgets) {
                val swingWidget = createSwingWidget(widget)
                containerPane.add(swingWidget, constraints)
            }
        }
    }

    private fun createSwingWidget(widget: Pagelet.Widget): SwingWidget {
        val swingWidget = SwingWidget.create(widget)
        swingWidget.addUpdateListener { obj ->
            notifyUpdateListeners(obj)
        }
        swingWidget.listenTo?.let {
            allSwingWidgets[it]?.let {
                it.addUpdateListener { _ ->
                    scrollPane?.let {remove(it) }
                    remove(containerPane)
                    containerPane = JPanel()
                    initWidgets()
                    parent.revalidate()
                    parent.repaint()
                }
            }
        }

        allSwingWidgets[widget.name] = swingWidget
        return swingWidget
    }

    /**
     * Returns non-null if pagelet has only one widget, and it is
     * of the specified type.
     */
    private fun findSoloWidget(widgets: List<Pagelet.Widget>, type: String): Pagelet.Widget? {
        if (widgets.size != 1) {
            return null
        }
        return widgets.find { it.type == type }
    }

    private fun getBackgroundColor(): Color {
        return UIManager.getColor("EditorPane.background")
    }


}