// @vitest-environment happy-dom
import { createApp, defineComponent, h, nextTick } from 'vue'
import { expect, test, vi } from 'vitest'
import RegisterPage from './RegisterPage.vue'
const mocks=vi.hoisted(()=>({ api:vi.fn(), replace:vi.fn() }))
vi.mock('../api',()=>({api:mocks.api,jsonRequest:(method:string,body:unknown)=>({method,body:JSON.stringify(body)})}))
vi.mock('../router',()=>({router:{replace:mocks.replace}}))

test('confirmation prevents submission and duplicate failure allows correction and retry',async()=>{
  mocks.api.mockReset();mocks.replace.mockReset()
  const root=document.createElement('div'),app=createApp(RegisterPage)
  app.component('ElInput',defineComponent({props:['modelValue','type'],emits:['update:modelValue'],setup:(props,{emit})=>()=>h('input',{value:props.modelValue,type:props.type,onInput:(event:Event)=>emit('update:modelValue',(event.target as HTMLInputElement).value)})}))
  app.component('ElButton',defineComponent({setup:(_, {slots})=>()=>h('button',{type:'submit'},slots.default?.())}))
  app.component('ElAlert',defineComponent({props:['title'],setup:props=>()=>h('p',props.title)}))
  app.component('RouterLink',defineComponent({setup:(_, {slots})=>()=>h('a',slots.default?.())}))
  app.mount(root)
  const set=async(index:number,value:string)=>{const input=root.querySelectorAll('input')[index]!;input.value=value;input.dispatchEvent(new Event('input'));await nextTick()}
  const submit=async()=>{root.querySelector('form')!.dispatchEvent(new Event('submit',{cancelable:true}));await Promise.resolve();await nextTick()}
  try {
    await set(0,'new_student');await set(1,'Demo@123456');await set(2,'different')
    await submit();expect(mocks.api).not.toHaveBeenCalled();expect(root.textContent).toContain('两次密码不一致')
    await set(2,'Demo@123456');mocks.api.mockRejectedValueOnce(new Error('用户名已存在'))
    await submit();expect(root.textContent).toContain('用户名已存在')
    await set(0,'another_student');mocks.api.mockResolvedValueOnce({role:'USER'})
    await submit();expect(JSON.parse(mocks.api.mock.calls[1]![1].body)).toEqual({username:'another_student',password:'Demo@123456'})
    expect(mocks.replace).toHaveBeenCalledWith({path:'/login',query:{registered:'1'}})
  } finally {app.unmount()}
})
