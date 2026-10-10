import { render } from 'preact'
import { App } from './App'
import { Auth } from './auth'
import './style.css'
import './settingsAppearance'

// Coming back from Google's sign-in page: take the token out of the address before anything else.
const authError = Auth.consumeRedirect()

render(<App authError={authError} />, document.getElementById('app')!)
