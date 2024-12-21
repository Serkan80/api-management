function spa() {
	return {
        currentRoute: '/',
        currentPage: '',
        routes: {
            '/': 'index.html',
            '/login': '../login.html',
            '/subscription': 'subscription.html',
            '/subscriptions': 'subscriptions.html',
            '/apis': 'apis.html',
            '/apis-form': 'apis-form.html',
            '/analytics': 'analytics.html'
        },
        username: null,
        roles: [],

        // Initialize the SPA
        init() {
            const loggedIn = this.isLoggedIn();
            if (!loggedIn) {
                this.determineAuthentication();
            } else {
                this.username = sessionStorage.getItem('username');
                this.roles = sessionStorage.getItem('roles').split(',');
                window.addEventListener('hashchange', () => this.loadRoute());
            }
        },

        // Load the route based on the hash
        loadRoute() {
            this.currentRoute = window.location.hash.slice(1) || '/';
            this.loadPage(this.currentRoute);
        },

        // Dynamically load page content
        async loadPage(route) {
            const page = this.routes[route];
            if (page) {
                this.currentPage = fetchPage(page);
            } else {
                this.currentPage = fetchPage('../404.html');
            }
        },

        determineAuthentication() {
            const options = { headers: {'Content-Type': 'application/json'}};
            fetch("http://localhost:8080/apim/core/apis", options)
                .then(res => {
                    if (res.status >= 400) {
                        window.location.href = '../login.html';
                    }
                    // else a redirect occurs to the OIDC server
                })
                .catch(err => {
//                    console.log(`err response from backend: ${err}`);
                    window.location.href = '../login.html';
                });
        },

        isLoggedIn() {
            return sessionStorage.getItem('username') !== null;
        },

        logout() {
            sessionStorage.clear();
            this.init();
        }
    };
}

async function fetchPage(page) {
    let res = await fetch(page);
    if (!res.ok) {
        res = await fetch('../404.html');
        return await res.text();
    }
    return await res.text();
}

function fetchData(url) {
    return {
	    isLoading: false,
		data: [],
		postData: {},
		searchQuery: null,
		errors: null,
		baseUrl: 'http://localhost:8080/apim/core',

		get() {
		    this.isLoading = true;
		    const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include' };

			fetch(`${this.baseUrl}${url}`, options)
				.then(res => res.json())
				.then(json => {
					this.isLoading = false;
					this.data = json;
				});
		},

		search(searchUrl) {
            this.isLoading = true;
            const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include' };

            fetch(`${this.baseUrl}${url}${searchUrl}${this.searchQuery}`, options)
                .then(res => res.json())
                .then(json => {
                    this.isLoading = false;
                    this.data = json;
                });
        },

		post(formId, to) {
		    let form = document.querySelector(formId);
	        form.classList.add('was-validated');

            if (form.checkValidity()) {
                const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include', method: 'post', body: JSON.stringify(this.postData) };
                fetch(`${this.baseUrl}${url}`, options)
                    .then(res => {
                        if (!res.ok) {
                            return res.json().then(err => {
                                if (err.violations) {
                                    this.errors = new Array();
                                    err.violations.forEach(ex => this.errors.push(`<li>${ex.field.split('.').at(-1)}: ${ex.message}</li>`));
                                    this.errors = "<ul style='margin:0'>" + this.errors.join([separator='\n']) + "</ul>";
                                } else if (err.message) {
                                    this.errors = err.message;
                                } else {
                                    this.errors = Object.values(err).join([separator='\n']);
                                }
                            });
                        }
                    })
                   .then(data => {
                        console.log("forwarding to: " + to);
                        this.loadPage(to);
                   })
                   .catch(err => console.log(err));
            }
		}
	}
}

function authBasic() {
	return {
		username: null,
		password: null,

		login() {
			console.log(`${username}, ${this.password}`);
			const options = {
				headers: {'Content-Type': 'application/json', 'Authorization': 'Basic ' + btoa(`${this.username}:${this.password}`)},
				method: 'post',
				credentials: 'include'
			};

            fetch('http://localhost:8080/apim/auth/token/web', options)
                .then(res => {
                    if (res.ok)
                      return res.json();
                    else
                      throw new Error("Authentication failed: " + res);
                })
                .then(data => {
                    sessionStorage.setItem("username", data.username);
                    sessionStorage.setItem("roles", data.roles);
//                    sessionStorage.setItem("at", data.access_token);
//                    sessionStorage.setItem("rt", data.refresh_token);
                    window.location.href = "pages/index.html";
                })
                .catch((err) => console.log(err));
		}
	}
}