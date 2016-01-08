//Dont forget that in order for this to work the table must have an extra column, specified by columnName, that serves as a container/reference value for the checkbox data.
var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};

  var DeleteButton = Class.create({
    initialize: function(getPatientIds) {
      this.getPatientIds = getPatientIds;
      this.element = new Element('a', {'class' : '', 'href' : ''});
      this.element.insert(new Element('span', {'class': 'fa fa-times'}));
      this.element.insert(" Delete");
      this.element.observe('click', this.onClick.bind(this));
    },
    getElement: function() {
      return this.element //this.element.wrap('div', { 'class': 'buttonwrapper' })
    },
    onClick: function(e) {
      e.preventDefault();
      this.patientIds = this.getPatientIds();
      this.dialogContents = new Element('div', {'class': 'delete-patients-dialog'});
      this.dialog = new PhenoTips.widgets.ModalPopup(this.dialogContents, false, {'title': "Delete Patients", 'removeOnClose': true});
      this.createDialogContents();
      this.dialog.show();
    },
    createDialogContents: function() {
      this.dialogContents.insert(this.getDialogMessage())
                         .insert(this.getDialogConfirmButton())
                         .insert(this.getDialogCancelButton());
    },
    getDialogMessage: function() {
      return (new Element('p')).update('You are about to delete '+this.patientIds.length+' patients. <br/> Are you sure?');
    },
    getDialogConfirmButton: function() {
      var button = new Element('a', {'class' : 'button', 'href' : ''});
      button.innerHTML = "Confirm";
      button.observe('click', this.launchRequest.bind(this));
      return button.wrap('div', {'class': 'buttonwrapper'});
    },
    getDialogCancelButton: function() {
      var button = new Element('a', {'class' : 'button secondary', 'href' : ''});
      button.innerHTML = "Cancel";
      button.observe('click', this.dialog.close.bind(this.dialog));
      return button.wrap('div', {'class': 'buttonwrapper'});
    },
    launchRequest: function(e) {
      e.preventDefault();
      if(this.activeRequest){
        return
      };
      var self = this;
      this.dialogContents.update('');
      
      var numPatients = this.patientIds.length;
      var progressBar = new PhenoTips.widgets.ProgressBar();
      this.dialogContents.update("<p>Deleting patients...</p>")
                         .insert(progressBar.render());
      var progress = {
        success: 0,
        failure: 0
      };
      var updateBar = (function() {
        return function() {
          progressBar.update((progress.success+progress.failure)/numPatients * 100);
        }.bind(this);
      }.bind(this))()
      this.patientIds.forEach(function(id){
        //Prototype.js does not support 'DELETE' request method.
        var request = new XMLHttpRequest();
        request.open('DELETE', '/rest/patients/'+id);
        request.onload = function() {
          if (request.status >= 200 && request.status < 400) {
            progress.success++;
          } else {
            progress.failure++;
          }
          updateBar();
          if (progress.success + progress.failure == numPatients) {
            self.onFinishedDeleting(progress);
          }
        };
        request.send();
      });
    },
    onFinishedDeleting: function(stats){
      this.dialogContents.update('');
      var deletionMessage = 'Done';
      var message = (new Element('p')).insert(deletionMessage);
      var okButton = new Element('a', {'class' : 'button', 'href' : ''});
      okButton.innerHTML='OK';
      okButton.observe('click', function() {
        this.dialog.closeDialog;
      }.bind(this));
      okButton = okButton.wrap('div', {'class': 'buttonwrapper'});
      this.dialogContents.insert(message)
                         .insert(okButton);
    }
  });

  var ModifyButton = Class.create({
    initialize: function(getPatientIds) {
      this.element = new Element('a', {'class' : '', 'href' : ''});
      this.element.insert(new Element('span', {'class': 'fa fa-wrench'}));
      this.element.insert(" Modify Permissions");
      this.element.observe('click', this.onClick.bind(this));
    },
    getElement: function() {
      return this.element;//.wrap('div', { 'class': 'buttonwrapper' });
    },
    onClick: function(e) {
      e.preventDefault();
      //do something
      console.log('Clicked!');
    }
  });

  widgets.LivetableCheckboxColumn = Class.create({
    tableId: null ,
    columnIndex: null ,
    columnName: null ,
    cbValuesContainer: null ,
    serviceDocument: null ,
    initialize: function(parameters) {
      this.tableId = parameters.tableId;
      this.columnName = parameters.columnName;
      this.serviceDocument = parameters.serviceDocument;
      this.initializeCBValuesContainer()

      this.initializeCheckboxes()
      
      this.initializeButtons()
    },
    initializeButtons: function() {
      //TODO: hook or explicit?
      buttons = {
        delete: new DeleteButton(this.getCheckedPatients.bind(this)),
        modify: new ModifyButton(this.getCheckedPatients.bind(this))
      }
      this.buttonsContainer = new Element('div', {
          id: this.tableId + "-batchButtons", "class": "livetable-CBvalues hidden"
        });
      var generalTools = $$('.general-tools');
      if(generalTools[0]) {
        generalTools[0].insert({top: this.buttonsContainer});
      } else {
        $(this.tableId).insert({
          before: this.buttonsContainer
        });
      }

      for (buttonName in buttons) {
        if (buttons.hasOwnProperty(buttonName)) {
          this.buttonsContainer.insert({
            bottom: buttons[buttonName].getElement()
          });
          this.buttonsContainer.insert({
            bottom: " · "
          });
        }
      }
    },
    initializeCheckboxes: function() {
      //Add 'master' checkbox
      var masterCell = $(this.tableId)
        .down(".xwiki-livetable-display-header")
        .down("tr")
        .next()
        .firstDescendant();

      this.masterCheck = new Element('input', {'type' : 'checkbox', 'name' : 'masterCheckboxSelect'});
      this.masterCheck.observe('click', this.onClickMasterCheck.bind(this));
      masterCell.insert(this.masterCheck); 

      //Add event listener to add checkboxes to rows as they are created
      if (this.columnIndex != null ) {
        document.observe("xwiki:livetable:" + this.tableId + ":newrow", function(i) {
          this.addCheckboxToRow(i)
        }
        .bind(this))
      }
    },

    initializeCBValuesContainer: function() {
      this.cbValuesContainer = $(this.tableId + "-CBValues");
      if (!this.cbValuesContainer) {
        this.cbValuesContainer = new Element("div",{
          id: this.tableId + "-CBValues",
          "class": "livetable-CBvalues hidden"
        });
        $(this.tableId).insert({
          before: this.cbValuesContainer
        });
        var headerRow = $(this.tableId).down(".xwiki-livetable-display-header").down("tr");
        var filterRow = headerRow.next();
        headerRow.select('th').forEach(function(th, index){
          if(th.down('a') && th.down('a').rel == this.columnName) {
            this.columnIndex = index;
          }
          if(index == 0) {
            // Add checkbox column header
            var checkboxTh = new Element('th', {'class' : 'xwiki-livetable-display-header-text'});           
            var checkboxTd = new Element('td', {'class' : 'xwiki-livetable-display-header-filter'});
            th.insert({
              'before' : checkboxTh
            });
            filterRow.insert({
              'top' : checkboxTd
            });
          }
        }.bind(this));
      };
    },

    addCheckboxToRow: function(event) {
        var row = event.memo.row;

        if(!row.down('input[name=checkboxSelect]')) {

          // The column could be hidden -> in that case, row doesn't have a corresponding td element for that column
          // This is why we're using the JSON data
          var data = event.memo.data;

          // Col name has to match the one in JSON
          var dataColName = this.columnName.replace(/doc./g, 'doc_')

          // Get the row value for the given column
          var rowValue = data[dataColName + '_value'];
          if(typeof rowValue === "undefined") {
            // Default values like doc_fullName ...
            rowValue = data[dataColName];
          }
          if(typeof rowValue !== "undefined") {
            var checkboxSelect = new Element('input', {'type' : 'checkbox', 'name' : 'checkboxSelect', 'value' : rowValue});
            this.cbValuesContainer.select('input').each(function(input){
              if(input.value === rowValue) {
                checkboxSelect.checked = true;
              }
            });
            row.insert({'top' : checkboxSelect});

            checkboxSelect.observe('click', this.onClickCheckBox.bind(this));
          }
        }
    },
    onClickCheckBox: function(e) {
      checkboxSelect = e.target
      if(checkboxSelect.checked) {
        //Add value to cbContainer
        var inputElem = new Element('input', {'name' : this.tableId + '-cb', 'value' : checkboxSelect.value});
        this.cbValuesContainer.insert({'bottom' : inputElem});
        checkboxSelect.up('tr').addClassName('highlighted')
      } else {
        //remove value from cbContainer
        this.cbValuesContainer.select('input').each(function(input){
          if(input.value === checkboxSelect.value) {
            input.parentNode.removeChild(input);
          }
        });
        checkboxSelect.up('tr').removeClassName('highlighted');
        //also remove check from master check if clicked
        if (this.masterCheck.checked) {
          this.masterCheck.checked = false;
        }
      }
      this.onSelectionChange();
    },
    onClickMasterCheck: function (e) {
      masterCheck = e.target;
      var table = $(this.tableId);
      if (masterCheck.checked) {
        table.select('input[name=checkboxSelect]').forEach(function(input){
          input.checked || input.click();
        })
      } else {
        table.select('input[name=checkboxSelect]').forEach(function(input){
          input.checked && input.click();
        })
      }
    },
    getCheckedPatients: function() {
      return this.cbValuesContainer.select('input').map(function(input){
        return input.value.replace('xwiki:data.', '');
      });
    },
    onSelectionChange: function() {
      if (this.getCheckedPatients().length > 0) {
        this.buttonsContainer.removeClassName('hidden');
      } else {
        this.buttonsContainer.addClassName('hidden');
      }
    }
  });
    
  return PhenoTips
}(PhenoTips || {}));

document.observe("xwiki:livetable:patients:loadingEntries", function(ev) {
    var params = {
        tableId : "patients",
        columnName : "doc.fullName",
        serviceDocument : new XWiki.Document("ServiceDocument", "Code", "xwiki"),
    }
    new PhenoTips.widgets.LivetableCheckboxColumn(params);
});
