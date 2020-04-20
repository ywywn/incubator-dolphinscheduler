/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
<template>
  <div class="clearfix dag-model" >
    <div class="toolbar">
      <!-- DAG界面左侧工具栏 -->
      <div class="title"><span>{{$t('Toolbar')}}</span></div>
      <div class="toolbar-btn">
        <div class="bar-box roundedRect jtk-draggable jtk-droppable jtk-endpoint-anchor jtk-connected"
             :class="v === dagBarId ? 'active' : ''"
             :id="v"
             :key="v"
             v-for="(item,v) in tasksTypeList"
             @mousedown="_getDagId(v)">
          <!-- taskTypeList 节点类型，拼接icos-' + v获取图标，:class动态获取icos图标-->
          <div data-toggle="tooltip" :title="item.description">
            <div class="icos" :class="'icos-' + v" ></div>
          </div>
        </div>
      </div>
    </div>
    <!-- DAG画布 -->
    <div class="dag-contect">
      <!-- DAG上部工具栏 -->
      <div class="dag-toolbar">
        <div class="assist-btn">
          <!-- 查看变量 -->
          <x-button
                  style="vertical-align: middle;"
                  data-toggle="tooltip"
                  :title="$t('View variables')"
                  data-container="body"
                  type="primary"
                  size="xsmall"
                  :disabled="$route.name !== 'projects-instance-details'"
                  @click="_toggleView"
                  icon="ans-icon-code">
          </x-button>
          <!-- 启动参数 -->
          <x-button
            style="vertical-align: middle;"
            data-toggle="tooltip"
            :title="$t('Startup parameter')"
            data-container="body"
            type="primary"
            size="xsmall"
            :disabled="$route.name !== 'projects-instance-details'"
            @click="_toggleParam"
            icon="ans-icon-arrow-circle-right">
          </x-button>
          <span class="name">{{name}}</span>
          &nbsp;
          <!-- 上部工具栏->复制名称 -->
          <span v-if="name"  class="copy-name" @click="_copyName" :data-clipboard-text="name"><i class="ans-icon-copy" data-container="body"  data-toggle="tooltip" :title="$t('Copy name')" ></i></span>
        </div>
        <div class="save-btn">
          <!-- 上部工具栏 -->
          <div class="operation" style="vertical-align: middle;">
            <a href="javascript:"
               v-for="(item,$index) in toolOperList"
               :class="_operationClass(item)"
               :id="item.code"
               :key="$index"
               @click="_ckOperation(item,$event)">
              <i :class="item.icon" data-toggle="tooltip" :title="item.description" ></i>
            </a>
          </div>
          <!-- 刷新DAG状态 -->
          <x-button
                  data-toggle="tooltip"
                  :title="$t('Refresh DAG status')"
                  data-container="body"
                  style="vertical-align: middle;"
                  icon="ans-icon-refresh"
                  type="primary"
                  :loading="isRefresh"
                  v-if="type === 'instance'"
                  @click="!isRefresh && _refresh()"
                  size="xsmall" >
          </x-button>
          <x-button
                  v-if="isRtTasks"
                  style="vertical-align: middle;"
                  type="primary"
                  size="xsmall"
                  icon="ans-icon-play"
                  @click="_rtNodesDag" >
            {{$t('Return_1')}}
          </x-button>
          <!-- 保存 -->
          <x-button
                  style="vertical-align: middle;"
                  type="primary"
                  size="xsmall"
                  :loading="spinnerLoading"
                  @click="_saveChart"
                  icon="ans-icon-save"
                  >
            {{spinnerLoading ? 'Loading...' : $t('Save')}}
          </x-button>
        </div>
      </div>
      <div class="scrollbar dag-container">
        <!-- DAG画布 -->
        <div class="jtk-demo" id="jtk-demo">
          <div class="jtk-demo-canvas canvas-wide statemachine-demo jtk-surface jtk-surface-nopan jtk-draggable" id="canvas" ></div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
  import _ from 'lodash'
  import Dag from './dag'
  import mUdp from './udp/udp'
  import i18n from '@/module/i18n'
  import { jsPlumb } from 'jsplumb'
  import Clipboard from 'clipboard'
  import { allNodesId } from './plugIn/util'
  import { toolOper, tasksType } from './config'
  import mFormModel from './formModel/formModel'
  import { formatDate } from '@/module/filter/filter'
  import { findComponentDownward } from '@/module/util/'
  import disabledState from '@/module/mixin/disabledState'
  import { mapActions, mapState, mapMutations } from 'vuex'

  // 抽屉,节点设置弹窗
  let eventModel
  //mFormModel -> 节点设置

  export default {
    name: 'dag-chart',
    data () {
      return {
        tasksTypeList: tasksType,
        toolOperList: toolOper(this),
        dagBarId: null,
        toolOperCode: '',
        spinnerLoading: false,
        urlParam: {
          id: this.$route.params.id || null
        },
        isRtTasks: false,
        isRefresh: false,
        isLoading: false,
        taskId: null
      }
    },
    mixins: [disabledState],
    props: {
      type: String,
      releaseState: String
    },
    methods: {
      ...mapActions('dag', ['saveDAGchart', 'updateInstance', 'updateDefinition', 'getTaskState']),
      ...mapMutations('dag', ['addTasks', 'resetParams', 'setIsEditDag', 'setName']),

      //初始化，若任务实例不为空回显工作流，否则执行Dag.create
      init () {
        if (this.tasks.length) {
          Dag.backfill()
          // Process instances can view status 工作流实例查看状态
          if (this.type === 'instance') {
            this._getTaskState(false).then(res => {})
            // Round robin acquisition status 循环获取状态
            this.setIntervalP = setInterval(() => {
              this._getTaskState(true).then(res => {})
            }, 90000)
          }
        } else {
          Dag.create()
        }
      },
      /**
       * copy name
       * 上部工具栏->复制名称
       */
      _copyName(){
        let clipboard = new Clipboard(`.copy-name`)
        clipboard.on('success', e => {
          this.$message.success(`${i18n.$t('Copy success')}`)
          // Free memory
          clipboard.destroy()
        })
        clipboard.on('error', e => {
          // Copy is not supported
          this.$message.warning(`${i18n.$t('The browser does not support automatic copying')}`)
          // Free memory
          clipboard.destroy()
        })
      },
      /**
       * Get state interface 任务示例中点击工作流实例所展示页面相关处理
       * @param isReset Whether to manually refresh
       */
      _getTaskState (isReset) {
        //resolve和reject，用于结束Promise等待的函数，对应的状态分别是成功和失败
        return new Promise((resolve, reject) => {
          this.getTaskState(this.urlParam.id).then(res => {
            let data = res.list
            let state = res.processInstanceState
            let taskList = res.taskList
            let idArr = allNodesId()
            const titleTpl = (item, desc) => {
              let $item = _.filter(taskList, v => v.name === item.name)[0]
              //悬浮框展示信息
              return `<div style="text-align: left">${i18n.$t('Name')}：${$item.name}</br>${i18n.$t('State')}：${desc}</br>${i18n.$t('type')}：${$item.taskType}</br>${i18n.$t('host')}：${$item.host || '-'}</br>${i18n.$t('Retry Count')}：${$item.retryTimes}</br>${i18n.$t('Submit Time')}：${formatDate($item.submitTime)}</br>${i18n.$t('Start Time')}：${formatDate($item.startTime)}</br>${i18n.$t('End Time')}：${$item.endTime ? formatDate($item.endTime) : '-'}</br></div>`
            }

            // remove tip state dom
            $('.w').find('.state-p').html('')

            //工作流节点回显
            data.forEach(v1 => {
              idArr.forEach(v2 => {
                if (v2.name === v1.name) {
                  let dom = $(`#${v2.id}`)
                  let state = dom.find('.state-p')
                  dom.attr('data-state-id', v1.stateId)
                  dom.attr('data-dependent-result', v1.dependentResult || '')
                  state.append(`<b class="${v1.icoUnicode} ${v1.isSpin ? 'as as-spin' : ''}" style="color:${v1.color}" data-toggle="tooltip" data-html="true" data-container="body"></b>`)
                  state.find('b').attr('title', titleTpl(v2, v1.desc))
                }
              })
            })
            //节点执行状态
            if (state === 'PAUSE' || state === 'STOP' || state === 'FAILURE' || this.state === 'SUCCESS') {
              // Manual refresh does not regain large json
              if (isReset) {
                findComponentDownward(this.$root, `${this.type}-details`)._reset()
              }
            }
            resolve()
          })
        })
      },
      /**
       * Get the action bar id
       * @param item
       */
      _getDagId (v) {
        // if (this.isDetails) {
        //   return
        // }
        this.dagBarId = v
        // console.log(v)
      },
      /**
       * operating
       */
      _ckOperation (item) {
        let is = true
        let code = ''

        if (!item.disable) {
          return
        }

        if (this.toolOperCode === item.code) {
          this.toolOperCode = ''
          code = item.code
          is = false
        } else {
          this.toolOperCode = item.code
          code = this.toolOperCode
          is = true
        }

        // event type DAG上部工具栏
        Dag.toolbarEvent({
          item: item,
          code: code,
          is: is
        })
      },
      _operationClass (item) {
        return this.toolOperCode === item.code ? 'active' : ''
        // if (item.disable) {
        //   return this.toolOperCode === item.code ? 'active' : ''
        // } else {
        //   return 'disable'
        // }
      },
      /**
       * Storage interface 工作流保存，与后台交互
       */
      _save (sourceType) {
        return new Promise((resolve, reject) => {
          this.spinnerLoading = true // Loading
          // Storage store 调用Dag.saveStore()，后执行then内方法
          Dag.saveStore().then(res => {
            if (this.urlParam.id) {
              /**
               * Edit
               * @param saveInstanceEditDAGChart => Process instance editing
               * @param saveEditDAGChart => Process definition editing
               */
              //依据类型调用updateInstance或updateDefinition接口
              // console.log(this)
              this[this.type === 'instance' ? 'updateInstance' : 'updateDefinition'](this.urlParam.id).then(res => {
                this.$message.success(res.msg)
                this.spinnerLoading = false
                resolve()
              }).catch(e => {
                this.$message.error(e.msg || '')
                this.spinnerLoading = false
                reject(e)
              })
            } else {
              // New 新建工作流保存，调用saveDAGchart接口
              this.saveDAGchart().then(res => {
                this.$message.success(res.msg)
                this.spinnerLoading = false
                // source @/conf/home/pages/dag/_source/editAffirmModel/index.js
                if (sourceType !== 'affirm') {
                  // Jump process definition
                  this.$router.push({ name: 'projects-definition-list' })
                }
                resolve()
              }).catch(e => {
                this.$message.error(e.msg || '')
                this.setName('')
                this.spinnerLoading = false
                reject(e)
              })
            }
          })
        })
      },
      /**
       * Global parameter
       * @param Promise
       */
      _udpTopFloorPop () {
        return new Promise((resolve, reject) => {
          let modal = this.$modal.dialog({
            closable: false,
            showMask: true,
            escClose: true,
            className: 'v-modal-custom',
            transitionName: 'opacityp',
            render (h) {
              //mUdp：保存DAG时的弹窗确认
              return h(mUdp, {
                on: {
                  onUdp () {
                    //校验后弹窗关闭
                    modal.remove()
                    resolve()
                  },
                  close () {
                    modal.remove()
                  }
                }
              })
            }
          })
        })
      },
      /**
       * Save chart 工作流保存，调用_save()
       */
      _saveChart () {
        // Verify node
        if (!this.tasks.length) {
          this.$message.warning(`${i18n.$t('Failed to create node to save')}`)
          return
        }

        // Global parameters (optional)
        this._udpTopFloorPop().then(() => {
          return this._save()
        })
      },
      /**
       * Return to the previous child node
       */
      _rtNodesDag () {
        let getIds = this.$route.query.subProcessIds
        let idsArr = getIds.split(',')
        let ids = idsArr.slice(0, idsArr.length - 1)
        let id = idsArr[idsArr.length - 1]
        let query = {}

        if (id !== idsArr[0]) {
          query = { subProcessIds: ids.join(',') }
        }
        let $name = this.$route.name.split('-')
        this.$router.push({ path: `/${$name[0]}/${$name[1]}/list/${id}`, query: query })
      },
      /**
       * Subprocess processing
       * @param subProcessId Subprocess ID
       */
      _subProcessHandle (subProcessId) {
        let subProcessIds = []
        let getIds = this.$route.query.subProcessIds
        if (getIds) {
          let newId = getIds.split(',')
          newId.push(this.urlParam.id)
          subProcessIds = newId
        } else {
          subProcessIds.push(this.urlParam.id)
        }
        let $name = this.$route.name.split('-')
        this.$router.push({ path: `/${$name[0]}/${$name[1]}/list/${subProcessId}`, query: { subProcessIds: subProcessIds.join(',') } })
      },
      /**
       * Refresh data
       */
      _refresh () {
        this.isRefresh = true
        this._getTaskState(false).then(res => {
          setTimeout(() => {
            this.isRefresh = false
            this.$message.success(`${i18n.$t('Refresh status succeeded')}`)
          }, 2200)
        })
      },
      /**
       * View variables
       */
      _toggleView () {
        findComponentDownward(this.$root, `assist-dag-index`)._toggleView()
      },

      /**
       * Starting parameters
       */
      _toggleParam () {
        findComponentDownward(this.$root, `starting-params-dag-index`)._toggleParam()
      },
      /**
       * Create a node popup layer
       * @param Object id
       */
      _createNodes ({ id, type }) {
        let self = this

        if (eventModel) {
          eventModel.remove()
        }

        const removeNodesEvent = (fromThis) => {
          // Manually destroy events inside the component
          fromThis.$destroy()
          // Close the popup
          eventModel.remove()
        }

        this.taskId = id

        // 抽屉

        eventModel = this.$drawer({
          closable: false,
          direction: 'right',
          escClose: true,
          render: h => h(mFormModel, {
            on: {
              addTaskInfo ({ item, fromThis }) {
                self.addTasks(item)
                setTimeout(() => {
                  removeNodesEvent(fromThis)
                }, 100)
              },
              close ({ flag, fromThis }) {
                // Edit status does not allow deletion of nodes
                if (flag) {
                  jsPlumb.remove(id)
                }

                removeNodesEvent(fromThis)
              },
              onSubProcess ({ subProcessId, fromThis }) {
                removeNodesEvent(fromThis)
                self._subProcessHandle(subProcessId)
              }
            },
            props: {
              id: id,
              taskType: type || self.dagBarId,
              self: self
            }
          })
        })
      }
    },
    watch: {
      'tasks': {
        deep: true,
        handler (o) {

          // Edit state does not allow deletion of node a...
          this.setIsEditDag(true)
        }
      }
    },
    created () {
      // Edit state does not allow deletion of node a...
      this.setIsEditDag(false)

      if (this.$route.query.subProcessIds) {
        this.isRtTasks = true
      }

      Dag.init({
        dag: this,
        instance: jsPlumb.getInstance({
          Endpoint: [
            'Dot', { radius: 1, cssClass: 'dot-style' }
          ],
          Connector: 'Straight',
          PaintStyle: { lineWidth: 2, stroke: '#456' }, // Connection style 连接风格
          ConnectionOverlays: [
            [
              'Arrow',
              {
                location: 1,
                id: 'arrow',
                length: 12,
                foldback: 0.8
              }
            ]
          ],
          Container: 'canvas'
        })
      })
    },
    mounted () {
      this.init()
    },
    beforeDestroy () {
      this.resetParams()

      // Destroy round robin
      clearInterval(this.setIntervalP)
    },
    destroyed () {
    },
    computed: {
      ...mapState('dag', ['tasks', 'locations', 'connects', 'isEditDag', 'name'])
    },
    components: {}
  }
</script>

<style lang="scss" rel="stylesheet/scss">
  @import "./dag";
</style>
